package app

import org.mockito.Mockito
import org.mockito.verification.VerificationMode

class TestUtils {

    companion object {

        fun once(): VerificationMode? {
            return Mockito.times(1)
        }
    }
}