module octapoint_move::octapoint {
    use sui::event;

    const E_INSUFFICIENT_BALANCE: u64 = 1;
    const E_SEQ_MISMATCH: u64 = 2;

    public struct AdminCap has key, store {
        id: UID,
    }

    public struct Balance has key {
        id: UID,
        amount: u64,
    }

    public struct CreditAccount has key, store {
        id: UID,
        tenant_id: vector<u8>,
        customer_id: vector<u8>,
        balance: u64,
        seq: u64,
    }

    public struct SettlementEvent has copy, drop {
        event_id: vector<u8>,
        action: vector<u8>,
        amount: u64,
        seq: u64,
    }

    fun init(ctx: &mut TxContext) {
        transfer::transfer(
            AdminCap { id: object::new(ctx) },
            tx_context::sender(ctx),
        );

        transfer::share_object(Balance {
            id: object::new(ctx),
            amount: 0,
        });
    }

    public fun issue(
        _: &AdminCap,
        balance: &mut Balance,
        amount: u64,
    ) {
        balance.amount = balance.amount + amount;
    }

    public fun redeem(
        _: &AdminCap,
        balance: &mut Balance,
        amount: u64,
    ) {
        assert!(balance.amount >= amount, E_INSUFFICIENT_BALANCE);
        balance.amount = balance.amount - amount;
    }

    public fun balance(balance: &Balance): u64 {
        balance.amount
    }

    public fun create_account(
        _: &AdminCap,
        tenant_id: vector<u8>,
        customer_id: vector<u8>,
        ctx: &mut TxContext,
    ) {
        let account = CreditAccount {
            id: object::new(ctx),
            tenant_id,
            customer_id,
            balance: 0,
            seq: 0,
        };
        transfer::transfer(account, tx_context::sender(ctx));
    }

    public fun settle_issue(
        _: &AdminCap,
        account: &mut CreditAccount,
        amount: u64,
        expected_seq: u64,
        event_id: vector<u8>,
    ) {
        assert!(account.seq == expected_seq, E_SEQ_MISMATCH);
        account.balance = account.balance + amount;
        account.seq = expected_seq + 1;
        event::emit(SettlementEvent {
            event_id,
            action: b"ISSUE",
            amount,
            seq: expected_seq,
        });
    }

    public fun settle_redeem(
        _: &AdminCap,
        account: &mut CreditAccount,
        amount: u64,
        expected_seq: u64,
        event_id: vector<u8>,
    ) {
        assert!(account.seq == expected_seq, E_SEQ_MISMATCH);
        assert!(account.balance >= amount, E_INSUFFICIENT_BALANCE);
        account.balance = account.balance - amount;
        account.seq = expected_seq + 1;
        event::emit(SettlementEvent {
            event_id,
            action: b"REDEEM",
            amount,
            seq: expected_seq,
        });
    }

    public fun account_balance(account: &CreditAccount): u64 {
        account.balance
    }

    public fun account_seq(account: &CreditAccount): u64 {
        account.seq
    }

    #[test_only]
    public fun create_balance_for_testing(ctx: &mut TxContext): Balance {
        Balance {
            id: object::new(ctx),
            amount: 0,
        }
    }

    #[test_only]
    public fun create_admin_cap_for_testing(ctx: &mut TxContext): AdminCap {
        AdminCap {
            id: object::new(ctx),
        }
    }

    #[test_only]
    public fun create_credit_account_for_testing(
        tenant_id: vector<u8>,
        customer_id: vector<u8>,
        ctx: &mut TxContext,
    ): CreditAccount {
        CreditAccount {
            id: object::new(ctx),
            tenant_id,
            customer_id,
            balance: 0,
            seq: 0,
        }
    }

    #[test_only]
    public fun destroy_balance_for_testing(balance: Balance) {
        let Balance { id, amount: _ } = balance;
        object::delete(id);
    }

    #[test_only]
    public fun destroy_admin_cap_for_testing(cap: AdminCap) {
        let AdminCap { id } = cap;
        object::delete(id);
    }

    #[test_only]
    public fun destroy_credit_account_for_testing(account: CreditAccount) {
        let CreditAccount { id, tenant_id: _, customer_id: _, balance: _, seq: _ } = account;
        object::delete(id);
    }
}
