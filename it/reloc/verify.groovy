{ project ->
    def wanted = [
            'a/ResultPrinter.class',
            'a/TestRunner.class',
            'b/Assert.class',
            'b/AssertionFailedError.class',
            'b/ComparisonCompactor.class',
            'b/ComparisonFailure.class',
            'b/Protectable.class',
            'b/Test.class',
            'b/TestCase.class',
            'b/TestFailure.class',
            'b/TestListener.class',
            'b/TestResult$1.class',
            'b/TestResult.class',
            'b/TestSuite$1.class',
            'b/TestSuite.class'
    ]
    def unwanted = [
            'junit/textui/ResultPrinter.class',
            'junit/textui/TestRunner.class',
            'junit/framework/Assert.class',
            'junit/framework/AssertionFailedError.class',
            'junit/framework/ComparisonCompactor.class',
            'junit/framework/ComparisonFailure.class',
            'junit/framework/Protectable.class',
            'junit/framework/Test.class',
            'junit/framework/TestCase.class',
            'junit/framework/TestFailure.class',
            'junit/framework/TestListener.class',
            'junit/framework/TestResult$1.class',
            'junit/framework/TestResult.class',
            'junit/framework/TestSuite$1.class',
            'junit/framework/TestSuite.class'
    ]

    def jar = file("${buildDir}/distributions/${project.name}-${currentVersion}.jar")

    assert jar.exists()

    def jarFile = zipTree(jar)

    wanted.each { item ->
        assert !jarFile.matching {
            include { fileTreeElement ->
                return fileTreeElement.relativePath.toString() == item
            }
        }.isEmpty(), "Did not find $item in jar"
    }

    unwanted.each { item ->
        assert jarFile.matching {
            include { fileTreeElement ->
                fileTreeElement.relativePath.toString() == item
            }
        }.isEmpty(), "Found $item in jar"
    }
}
