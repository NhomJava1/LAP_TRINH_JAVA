package com.octapoint.core.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.octapoint.core")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule modular_boundaries_are_respected = classes()
        .that().resideInAPackage("..tenant..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage("..tenant..", "..shared..", "java..", "jakarta..", "org.springframework..")
        .allowEmptyShould(true); 
}