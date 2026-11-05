package com.octapoint.core.architecture;

import com.octapoint.core.chain.ChainClient;
import com.octapoint.core.chain.MockChainClient;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.octapoint.core")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule modular_boundaries_are_respected = classes()
        .that().resideInAPackage("..tenant..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage("..tenant..", "..shared..", "java..", "jakarta..", "org.springframework..")
        .allowEmptyShould(true); 

    @ArchTest
    static final ArchRule chain_client_implementations_are_in_chain_module = classes()
        .that().implement(ChainClient.class)
        .should().resideInAPackage("..chain..");

    @ArchTest
    static final ArchRule other_modules_depend_on_chain_client_abstraction = noClasses()
        .that().resideOutsideOfPackage("..chain..")
        .should().dependOnClassesThat()
        .areAssignableTo(MockChainClient.class);
}