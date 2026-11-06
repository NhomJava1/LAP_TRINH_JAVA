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

            octapoint::destroy_balance_for_testing(balance);
            octapoint::destroy_admin_cap_for_testing(admin_cap);
        };

        test_scenario::end(scenario);
    }
}
