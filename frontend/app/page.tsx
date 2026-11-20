import Link from "next/link";

const API_DOCS_URL =
  (process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080").replace(/\/+$/, "") +
  "/swagger-ui.html";

export default function Home() {
  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950 text-white flex flex-col">
      <header className="border-b border-white/10 px-6 py-4 flex items-center justify-between max-w-6xl w-full mx-auto">
        <div className="flex items-center gap-2">
          <span className="inline-flex h-8 w-8 items-center justify-center rounded-lg bg-blue-500 font-bold text-white">
            O
          </span>
          <span className="font-bold text-lg tracking-tight">OctaPoint</span>
        </div>
        <nav className="flex items-center gap-3 text-sm">
          <Link
            href="/login"
            className="px-4 py-2 rounded-lg text-slate-200 hover:bg-white/10 transition"
          >
            Đăng nhập
          </Link>
          <Link
            href="/register"
            className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 font-medium transition"
          >
            Đăng ký Merchant
          </Link>
        </nav>
      </header>

      <main className="flex-1 max-w-6xl w-full mx-auto px-6 py-16 flex flex-col justify-center">
        <div className="max-w-3xl space-y-6">
          <div className="inline-flex items-center gap-2 rounded-full border border-blue-400/30 bg-blue-500/10 px-3 py-1 text-xs font-medium text-blue-300">
            MVP Credit-as-a-Service • PostgreSQL SSOT + Sui Testnet Settlement
          </div>
          <h1 className="text-4xl sm:text-5xl font-bold tracking-tight leading-tight">
            Nền tảng tích lũy &amp; quy đổi điểm thưởng đa Merchant trên Sui Blockchain
          </h1>
          <p className="text-slate-300 text-lg leading-relaxed">
            Xử lý giao dịch nghiệp vụ tức thời trên sổ cái PostgreSQL bất biến, bảo mật API bằng chữ ký HMAC-SHA256 + Redis chống phát lại, và đối soát bất đồng bộ lên Sui Testnet qua Outbox Worker.
          </p>
          <div className="flex flex-wrap gap-4 pt-2">
            <Link
              href="/login"
              className="px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-500 font-semibold transition"
            >
              Đăng nhập Portal
            </Link>
            <Link
              href="/register"
              className="px-6 py-3 rounded-lg bg-white text-slate-900 hover:bg-slate-100 font-semibold transition"
            >
              Đăng ký Merchant mới
            </Link>
            <a
              href={API_DOCS_URL}
              target="_blank"
              rel="noopener noreferrer"
              className="px-6 py-3 rounded-lg border border-white/20 hover:bg-white/10 font-semibold transition"
            >
              API Docs (Swagger UI)
            </a>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mt-16">
          <div className="rounded-xl border border-white/10 bg-white/5 p-6">
            <h2 className="font-semibold text-blue-300 mb-2">1. Đa Tenant &amp; Phê duyệt</h2>
            <p className="text-sm text-slate-300">
              Merchant đăng ký trực tuyến, Admin kiểm duyệt trạng thái PENDING → APPROVED, cấu hình rule tích/đổi điểm và phát hành API key mã hóa AES-256-GCM.
            </p>
          </div>
          <div className="rounded-xl border border-white/10 bg-white/5 p-6">
            <h2 className="font-semibold text-emerald-300 mb-2">2. HMAC-SHA256 &amp; Idempotency</h2>
            <p className="text-sm text-slate-300">
              Chuẩn xác thực server-to-server ADR-0003 với header X-OP-*, chống replay attack bằng Redis SETNX và đảm bảo không âm điểm khi redeem đồng thời.
            </p>
          </div>
          <div className="rounded-xl border border-white/10 bg-white/5 p-6">
            <h2 className="font-semibold text-purple-300 mb-2">3. Outbox &amp; Sui Explorer</h2>
            <p className="text-sm text-slate-300">
              Worker gom lô bất đồng bộ qua TypeScript Signer lên Sui Testnet, lưu transaction digest thật để kiểm chứng minh bạch và hỗ trợ retry khi lỗi.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
