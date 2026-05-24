package com.umit.budgettracker.core.export

import android.content.Context
import com.umit.budgettracker.core.domain.repository.ExpenseRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class CsvExportServiceTest {

    @Test
    fun testEscapeCsv() {
        val context: Context = mock()
        val repository: ExpenseRepository = mock()
        val service = CsvExportService(context, repository)
        
        assertEquals("Simple", service.escapeCsv("Simple"))
        assertEquals("\"With,Comma\"", service.escapeCsv("With,Comma"))
        assertEquals("\"With\nNewline\"", service.escapeCsv("With\nNewline"))
        assertEquals("\"With \"\"Quotes\"\"\"", service.escapeCsv("With \"Quotes\""))
    }
}
