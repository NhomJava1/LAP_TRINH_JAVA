module octapoint_move::octapoint {
    use sui::object::{Self, UID};
    use sui::transfer;
    use sui::tx_context::{Self, TxContext};

    public struct AdminCap has key, store {
        id: UID,
    }

    public struct Balance has key {
        id: UID,
        amount: u64,
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

    public fun balance(balance: &Balance): u64 {
        balance.amount
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
    public fun destroy_balance_for_testing(balance: Balance) {
        let Balance { id, amount: _ } = balance;
        object::delete(id);
    }

    #[test_only]
    public fun destroy_admin_cap_for_testing(cap: AdminCap) {
        let AdminCap { id } = cap;
        object::delete(id);
    }
}
