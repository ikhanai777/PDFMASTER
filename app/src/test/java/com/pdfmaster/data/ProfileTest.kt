package com.pdfmaster.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTest {
    private val p = Profile(
        fullName = "Sara Khan", firstName = "Sara", lastName = "Khan", email = "sara@example.com",
        phone = "+971 50 000 0000", city = "Dubai", postalCode = "00000", idNumber = "784-0000",
    )

    @Test fun matchesCommonFieldNames() {
        assertEquals("Sara", p.valueForField("FirstName"))
        assertEquals("Khan", p.valueForField("surname_1"))
        assertEquals("sara@example.com", p.valueForField("E-mail Address"))
        assertEquals("+971 50 000 0000", p.valueForField("Mobile"))
        assertEquals("Sara Khan", p.valueForField("Full Name"))
        assertEquals("784-0000", p.valueForField("Emirates ID"))
        assertEquals("Dubai", p.valueForField("City/Town"))
    }

    @Test fun unknownOrEmptyFieldsReturnNull() {
        assertNull(p.valueForField("Signature date"))
        assertNull(p.valueForField("Company"))
    }
}
