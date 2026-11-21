'use client';

import { useEffect, useState } from 'react';
import { apiFetch } from '@/lib/api';

interface AuditLogItem {
  id: string;
  tenantId: string | null;
  actor: string;
  actorRole: string | null;
  action: string;
  targetType: string | null;
  targetId: string | null;
  result: string;
  details: string | null;
  correlationId: string | null;
  createdAt: string | null;
}

export default function AdminAuditLogsPage() {
  const [logs, setLogs] = useState<AuditLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadLogs = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await apiFetch<AuditLogItem[]>('/api/admin/audit-logs');
      setLogs(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tải audit logs');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    apiFetch<AuditLogItem[]>('/api/admin/audit-logs')
      .then((data) => {
        if (active) setLogs(data);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : 'Không thể tải audit logs');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="max-w-6xl mx-auto space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Audit Logs Hệ thống</h1>
          <p className="text-sm text-slate-500 mt-1">
            Truy vết các thao tác quản trị, phát hành/thu hồi API key, chữ ký sai và replay attack (FR-15).
          </p>
        </div>
        <button
          onClick={loadLogs}
          className="px-3 py-1.5 rounded-lg border border-slate-300 bg-white text-sm hover:bg-slate-50"
        >
          Làm mới
        </button>
      </div>

      {error && (
        <div className="p-4 rounded-lg bg-red-50 border border-red-200 text-red-700 text-sm">
          {error}
        </div>
      )}

      <div className="bg-white border border-slate-200 rounded-xl overflow-x-auto shadow-sm">
        <table className="w-full text-left text-xs">
          <thead className="bg-slate-50 border-b border-slate-200 text-slate-600">
            <tr>
              <th className="p-3.5 font-semibold">Thời gian</th>
              <th className="p-3.5 font-semibold">Actor</th>
              <th className="p-3.5 font-semibold">Hành động</th>
              <th className="p-3.5 font-semibold">Đối tượng</th>
              <th className="p-3.5 font-semibold">Kết quả</th>
              <th className="p-3.5 font-semibold">Chi tiết</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={6} className="p-6 text-center text-slate-500">
                  Đang tải...
                </td>
              </tr>
            ) : logs.length === 0 ? (
              <tr>
                <td colSpan={6} className="p-6 text-center text-slate-500">
                  Chưa có bản ghi audit nào.
                </td>
              </tr>
            ) : (
              logs.map((log) => (
                <tr key={log.id} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="p-3.5 text-slate-500 whitespace-nowrap">
                    {log.createdAt ? new Date(log.createdAt).toLocaleString('vi-VN') : '-'}
                  </td>
                  <td className="p-3.5">
                    <div className="font-medium text-slate-900">{log.actor}</div>
                    <div className="text-slate-400">{log.actorRole || '-'}</div>
                  </td>
                  <td className="p-3.5 font-mono font-semibold text-slate-800">{log.action}</td>
                  <td className="p-3.5 font-mono text-slate-600">
                    {log.targetType}: {log.targetId || '-'}
                  </td>
                  <td className="p-3.5">
                    <span
                      className={`px-2 py-0.5 rounded font-semibold ${
                        log.result === 'SUCCESS'
                          ? 'bg-emerald-100 text-emerald-700'
                          : 'bg-red-100 text-red-700'
                      }`}
                    >
                      {log.result}
                    </span>
                  </td>
                  <td className="p-3.5 text-slate-600 max-w-sm truncate" title={log.details || ''}>
                    {log.details || '-'}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
