package com.shreenipane.orbit

import com.shreenipane.orbit.core.parse.CalendarParsers
import com.shreenipane.orbit.core.parse.LoginParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginAndDaTest {

    @Test fun loginPieces() {
        val page = """<form id="vtopLoginForm"><input type="hidden" name="_csrf" value="ab12-cd34"/>
            <img src="data:image/jpeg;base64,/9j/4AAQSk
            ZJRg==" /><input name="captchaStr"/></form>"""
        assertEquals("ab12-cd34", LoginParsers.csrf(page))
        assertEquals("/9j/4AAQSkZJRg==", LoginParsers.captchaBase64(page))
        assertTrue(!LoginParsers.usesRecaptcha(page))
        assertTrue(LoginParsers.usesRecaptcha("""<div class="g-recaptcha" data-sitekey="x"></div>"""))
    }

    @Test fun loginOutcomes() {
        assertEquals(LoginParsers.Outcome.WrongCaptcha,
            LoginParsers.outcome("https://vtop.vit.ac.in/vtop/login/error", "<strong>Invalid Captcha</strong>"))
        val otp = LoginParsers.outcome("https://vtop.vit.ac.in/vtop/login/error",
            """<form id="securityOtpForm"><input name="_csrf" value="z9"/></form>""")
        assertEquals(LoginParsers.Outcome.OtpRequired("z9"), otp)
        val bad = LoginParsers.outcome("https://vtop.vit.ac.in/vtop/login/error", "<strong>Invalid LoginId/Password</strong>")
        assertEquals(LoginParsers.Outcome.Failed("Invalid LoginId/Password"), bad)
        val ok = LoginParsers.outcome("https://vtop.vit.ac.in/vtop/content",
            """<input name="_csrf" value="c1"/><input type="hidden" name="authorizedIDX" value="24BCE2903"/>""")
        assertEquals(LoginParsers.Outcome.Success, ok)
        val r = LoginParsers.otpReply("""{"status":"SUCCESS","message":"ok","redirectUrl":"/vtop/content"}""")
        assertEquals("SUCCESS", r.status)
        assertEquals("/vtop/content", r.redirectUrl)
    }

    @Test fun daCoursesByContent() {
        // An extra leading column and the class number only in an onclick: still found.
        val html = """<table><tr><td>Sl.No.</td><td>Group</td><td>Course Code</td><td>Title</td><td>Type</td><td>Faculty</td><td></td><td></td></tr>
          <tr onclick="myFunction('VL2025260100777')"><td>1</td><td>General</td><td>BCSE204L</td><td>Design and Analysis of Algorithms</td><td>Theory Only</td><td>SRIVANI A</td><td>x</td><td>y</td></tr></table>"""
        val c = CalendarParsers.daCourses(html)
        assertEquals(1, c.size)
        assertEquals("VL2025260100777", c[0].classId)
        assertEquals("BCSE204L", c[0].code)
        assertEquals("Design and Analysis of Algorithms", c[0].title)
    }

    @Test fun daListWithUploadCode() {
        val course = CalendarParsers.DaCourse("VL1234567890123", "BCSE204L", "DAA", "Theory Only")
        val html = """<table>
          <tr><td>1</td><td>Digital Assignment 1</td><td>10</td><td>10</td><td>13-Nov-2026</td>
              <td><a href="javascript:vtopDownload('examinations/downloadQp/VL1/DA1')">Download</a></td>
              <td>File Not Uploaded</td><td><i class="fa fa-pencil"></i><input type="hidden" name="code" value="DA01"/></td><td></td></tr>
        </table>"""
        val list = CalendarParsers.daList(html, course)
        assertEquals(1, list.size)
        val d = list[0]
        assertEquals("13-Nov-2026", d.dueDate)
        assertEquals("DA01", d.mcode)
        assertTrue(d.canUpload)
        assertTrue(!d.submitted)
        assertEquals("examinations/downloadQp/VL1/DA1", d.qpUrl)
    }

    @Test fun uploadResponses() {
        assertEquals(CalendarParsers.UploadResult.Success, CalendarParsers.uploadResult("<span>Uploaded successfully</span>"))
        assertEquals(CalendarParsers.UploadResult.OtpRequired,
            CalendarParsers.uploadResult("<span>shree@vitstudent.ac.in</span><span></span><span></span>"))
        assertEquals(CalendarParsers.UploadResult.Failed("Invalid OTP. Please try again."),
            CalendarParsers.uploadResult("<span>shree@vitstudent.ac.in</span><span></span><span>Invalid OTP. Please try again.</span>"))
        assertEquals(CalendarParsers.UploadResult.Failed("File size exceeds"),
            CalendarParsers.uploadResult("<span>Failed</span><span>File size exceeds</span>"))
        assertEquals(listOf("c1" to "o1", "c2" to "o2"), CalendarParsers.uploadTokens(
            """<input name="code" value="c1"/><input name="opt" value="o1"/><input name="code" value="c2"/><input name="opt" value="o2"/>"""))
    }
}

class AttendanceAdviceTest {
    private fun a(att: Int, tot: Int, rem: Int?) = com.shreenipane.orbit.core.calc.AttendanceAdvice.of(att, tot, rem)

    /** Every branch must build its text without throwing: the v0.5 crash was a bad format string here. */
    @Test fun everyBranch() {
        val out = a(24, 40, 2)                // out of reach
        assertEquals("Max 62%", out.tag)
        assertEquals("Can't reach 75% · best is 61.9%", out.line)
        assertEquals("Attend all", a(26, 36, 4).tag)        // 30/40 = 75% exactly
        assertEquals("Attend all", a(28, 40, 10).tag)       // 38/50 = 76% needs all ten
        assertEquals("Attend 16", a(29, 40, 20).tag)       // 45/60 = 75% with 16 of 20
        assertEquals("Skip 8", a(36, 40, 10).tag)        // 38/50 = 76%
        assertEquals("Done", a(30, 40, 0).tag)
        assertEquals("Attend 8", a(7, 12, null).tag)
        assertEquals("Skip 1", a(30, 39, null).tag)
        assertEquals("Don't skip", a(30, 40, null).tag)       // exactly 75%
    }
}
