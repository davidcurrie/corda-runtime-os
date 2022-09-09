package net.corda.internal.serialization.amqp

import net.corda.internal.serialization.amqp.testutils.TestSerializationOutput
import net.corda.internal.serialization.amqp.testutils.deserialize
import net.corda.internal.serialization.amqp.testutils.testDefaultFactory
import net.corda.internal.serialization.amqp.testutils.testName
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.io.NotSerializableException
import net.corda.v5.base.annotations.CordaSerializable
import org.junit.jupiter.api.Assertions.assertEquals

class ErrorMessagesTests {
    companion object {
        val VERBOSE get() = false
    }

    private fun errMsg(property: String, testname: String) =
        "Property '$property' or its getter is non public, this renders class 'class $testname\$C' unserializable -> class $testname\$C"

    // Java allows this to be set at the class level yet Kotlin doesn't for some reason
    @Test
    fun privateProperty() {
        data class C(private val a: Int)

        val sf = testDefaultFactory()

        val testname = "${javaClass.name}\$${testName()}"

        assertThatThrownBy {
            TestSerializationOutput(VERBOSE, sf).serialize(C(1))
        }.isInstanceOf(NotSerializableException::class.java).hasMessage(errMsg("a", testname))
    }

    // Java allows this to be set at the class level yet Kotlin doesn't for some reason
    @Test
    fun privateProperty2() {
        data class C(val a: Int, private val b: Int)

        val sf = testDefaultFactory()

        val testname = "${javaClass.name}\$${testName()}"

        assertThatThrownBy {
            TestSerializationOutput(VERBOSE, sf).serialize(C(1, 2))
        }.isInstanceOf(NotSerializableException::class.java).hasMessage(errMsg("b", testname))
    }

    // Java allows this to be set at the class level yet Kotlin doesn't for some reason
    @Test
    fun privateProperty3() {
        // despite b being private, the getter we've added is public and thus allows for the serialisation
        // of the object
        @CordaSerializable
        data class C(val a: Int, private val b: Int) {
            @Suppress("unused")
            fun getB() = b
        }

        val sf = testDefaultFactory()

        val input = C(1, 2)

        val bytes = TestSerializationOutput(VERBOSE, sf).serialize(input)
        val output = DeserializationInput(sf).deserialize(bytes)
        assertEquals(input, output)
    }

    // Java allows this to be set at the class level yet Kotlin doesn't for some reason
    @Test
    fun protectedProperty() {
        open class C(@Suppress("unused") protected val a: Int)

        val sf = testDefaultFactory()

        val testname = "${javaClass.name}\$${testName()}"

        assertThatThrownBy {
            TestSerializationOutput(VERBOSE, sf).serialize(C(1))
        }.isInstanceOf(NotSerializableException::class.java).hasMessage(errMsg("a", testname))
    }
}
