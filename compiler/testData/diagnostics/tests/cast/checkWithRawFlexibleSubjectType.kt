// FILE: Sample.java

public class Sample<T> {
    public static Sample getRawSample() {
        return null;
    }
}

// FILE: test.kt

class SubConcrete : Sample<Int>()

fun test() {
    val rawSample = Sample.getRawSample()
    if (rawSample is SubConcrete) {

    }
}