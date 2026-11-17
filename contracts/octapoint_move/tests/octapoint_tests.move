#[test_only]
module octapoint_move::octapoint_tests {
    use sui::test_scenario;
    use octapoint_move::octapoint;

    #[test]
    fun test_issue_increases_balance() {
        let mut scenario = test_scenario::begin(@0xA);

        {
            let mut balance = octapoint::create_balance_for_testing(
                test_scenario::ctx(&mut scenario),
            );
            let admin_cap = octapoint::create_admin_cap_for_testing(
                test_scenario::ctx(&mut scenario),
            );

            octapoint::issue(&admin_cap, &mut balance, 100);
            octapoint::issue(&admin_cap, &mut balance, 25);
            assert!(octapoint::balance(&balance) == 125, 0);

            octapoint::redeem(&admin_cap, &mut balance, 30);
            assert!(octapoint::balance(&balance) == 95, 1);

            octapoint::destroy_balance_for_testing(balance);
            octapoint::destroy_admin_cap_for_testing(admin_cap);
        };

        test_scenario::end(scenario);
    }

    #[test]
    fun test_credit_account_issue_redeem_sequence() {
        let mut scenario = test_scenario::begin(@0xA);
        {
            let ctx = test_scenario::ctx(&mut scenario);
            let admin_cap = octapoint::create_admin_cap_for_testing(ctx);
            let mut acc = octapoint::create_credit_account_for_testing(b"ten_1", b"C1", ctx);

            octapoint::settle_issue(&admin_cap, &mut acc, 100, 0, b"evt_1");
            assert!(octapoint::account_balance(&acc) == 100, 0);
            assert!(octapoint::account_seq(&acc) == 1, 1);

            octapoint::settle_redeem(&admin_cap, &mut acc, 30, 1, b"evt_2");
            assert!(octapoint::account_balance(&acc) == 70, 2);
            assert!(octapoint::account_seq(&acc) == 2, 3);

            octapoint::destroy_credit_account_for_testing(acc);
            octapoint::destroy_admin_cap_for_testing(admin_cap);
        };
        test_scenario::end(scenario);
    }

    #[test]
    #[expected_failure(abort_code = 2)]
    fun test_rejects_sequence_mismatch_replay() {
        let mut scenario = test_scenario::begin(@0xA);
        {
            let ctx = test_scenario::ctx(&mut scenario);
            let admin_cap = octapoint::create_admin_cap_for_testing(ctx);
            let mut acc = octapoint::create_credit_account_for_testing(b"ten_1", b"C1", ctx);

            octapoint::settle_issue(&admin_cap, &mut acc, 100, 0, b"evt_1");
            octapoint::settle_issue(&admin_cap, &mut acc, 100, 0, b"evt_1_dup");

            octapoint::destroy_credit_account_for_testing(acc);
            octapoint::destroy_admin_cap_for_testing(admin_cap);
        };
        test_scenario::end(scenario);
    }

    #[test]
    #[expected_failure(abort_code = 1)]
    fun test_rejects_insufficient_balance_redeem() {
        let mut scenario = test_scenario::begin(@0xA);
        {
            let ctx = test_scenario::ctx(&mut scenario);
            let admin_cap = octapoint::create_admin_cap_for_testing(ctx);
            let mut acc = octapoint::create_credit_account_for_testing(b"ten_1", b"C1", ctx);

            octapoint::settle_issue(&admin_cap, &mut acc, 50, 0, b"evt_1");
            octapoint::settle_redeem(&admin_cap, &mut acc, 100, 1, b"evt_2");

            octapoint::destroy_credit_account_for_testing(acc);
            octapoint::destroy_admin_cap_for_testing(admin_cap);
        };
        test_scenario::end(scenario);
    }
}
