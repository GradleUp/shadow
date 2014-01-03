{ project ->
    def wanted = [
            'a/ResultPrinter.class',
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
            'a/TestRunner.class',
            'b/Assert.class',
            'b/AssertionFailedError.class',
            'b/ComparisonCompactor.class',
            'b/ComparisonFailure.class',
            'b/Protectable.class'
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
