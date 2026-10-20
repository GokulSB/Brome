package com.ripple.chat.utils

import com.ripple.chat.data.SmsCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    @Test
    fun testBookMyShowTicketConfirmationWithRewardAndCongratulations() {
        val body = "BookMyShow: Yay! Your tickets are confirmed for Stree 2 at PVR: Vega City. Screen 4, Audi 4, Seats: Executive-E12, E13. Total Amount: Rs 540. Booking ID: WDE8765. Congratulations! Claim flat Rs 100 off on snacks with code BMSNACK."
        val category = CategoryClassifier.classify("VK-BMSHOW", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testRedBusTicketConfirmationWithRewardCoinsAndBitlyLink() {
        val body = "RedBus: Your booking is confirmed! PNR: RB12345678. From: Bangalore To: Chennai on 18 Oct 2026. Bus: SRS Travels (A/C Sleeper). Seat(s): L4, L5. Boarding: Madiwala at 22:30. Congratulations! You won 50 redBus reward coins. Track bus: https://bit.ly/rb-track"
        val category = CategoryClassifier.classify("VM-REDBUS", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testPvrCinemasTicketConfirmationWithCongratulations() {
        val body = "PVR Cinemas: Booking Confirmed! ID: PVR98765432. Movie: Jawan. Audi 02, Classic - G10, G11. Date: 16-Oct 04:00 PM. PVR Forum Mall. Congratulations on your booking!"
        val category = CategoryClassifier.classify("AD-PVRCIN", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testInoxCinemasTicketConfirmation() {
        val body = "INOX: Your ticket is confirmed for Avatar 3. Screen 2, Audi 03, Seats: F14, F15. Booking Ref: INX102938. Enjoy your movie!"
        val category = CategoryClassifier.classify("AX-INOXBX", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testCinepolisMovieTicketConfirmation() {
        val body = "Cinepolis: Tickets booked for Oppenheimer. Audi 5, Screen 1, Seats: G12, G13. Showtime: 19:45. Booking ID: CIN998811."
        val category = CategoryClassifier.classify("AX-CINEPL", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testAbhiBusTicketConfirmationWithBitlyTracking() {
        val body = "AbhiBus: Ticket Booked! Ref: AB8976543. Bangalore to Hyderabad. KSRTC Airavat Club Class. Dep: 21:00, 19-Oct. Seat: 21, 22. Boarding: Majestic. Track your bus: https://bit.ly/abhibus-live"
        val category = CategoryClassifier.classify("BZ-ABHIBUS", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testZingbusTicketConfirmation() {
        val body = "zingbus: Booking confirmed! Delhi to Manali on 22 Oct. Dep: 20:00. Boarding Pt: Majnu Ka Tilla. Seat: 12 (A/C Sleeper). PNR: ZB56789. Track your bus: https://bit.ly/zb-live"
        val category = CategoryClassifier.classify("VK-ZINGBS", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testGenericBusBookingConfirmationFromNumericSender() {
        val body = "Your bus ticket is booked from Pune to Goa on 20-Oct. Bus No: MH12-AB-9999. SRS Travels. Seat No: 15 (Window). Boarding Point: Swargate at 20:00. PNR: SRS987654."
        val category = CategoryClassifier.classify("9876543210", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testIrctcTrainTicketConfirmation() {
        val body = "PNR: 4234567890, Train: 12626 / KERALA EXPRESS, Date: 20-Oct, Sleeper (SL), NDLS to TVC, Boarding: NDLS, Quota: GN. Passenger 1: S4, 21, MB / CNF. Total Fare: Rs 780. Have a safe journey!"
        val category = CategoryClassifier.classify("CP-IRCTC", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testFlightTicketConfirmationWithBoardingPassLink() {
        val body = "IndiGo: Booking reference 6E-W8KZ3. Flight 6E 204 from BLR to DEL on 18 Oct. Dep: 06:10. Gate: 4. Terminal 1. Web check-in is open. Boarding pass: bit.ly/6e-bp"
        val category = CategoryClassifier.classify("6E-INDIGO", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testMakeMyTripHotelBooking() {
        val body = "MakeMyTrip: Reservation Confirmed at Hotel Grand Palace, Goa. Check-in: 25 Oct, Check-out: 28 Oct. Booking ID: NH7654321. Enjoy your stay!"
        val category = CategoryClassifier.classify("MM-MAKEMY", body)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testSpamScamWithLotteryAndCashPrize() {
        val body = "Congratulations! You won 10,000,000 cash prize in UK lucky draw! Claim your prize now at http://scam.link"
        val category = CategoryClassifier.classify("919988776655", body)
        assertEquals(SmsCategory.SPAM, category)
    }

    @Test
    fun testSpamScamMentioningMovieTicketAndCasino() {
        val body = "Win free movie tickets and cash prizes! Play online casino at 1xBet. Deposit now to claim 100% bonus: bit.ly/scambet"
        val category = CategoryClassifier.classify("918877665544", body)
        assertEquals(SmsCategory.SPAM, category)
    }

    @Test
    fun testWorkFromHomeSpamScam() {
        val body = "Earn Rs 5,000 to Rs 10,000 per day from home using mobile phone! No experience required. Click here to start: bit.ly/wfh-scam"
        val category = CategoryClassifier.classify("919876500000", body)
        assertEquals(SmsCategory.SPAM, category)
    }

    @Test
    fun testCryptoScam() {
        val body = "Double your money in 24 hours! Invest 1000 and get 5000 in crypto wallet. Guaranteed returns: bit.ly/cryptotrade"
        val category = CategoryClassifier.classify("919876511111", body)
        assertEquals(SmsCategory.SPAM, category)
    }

    @Test
    fun testBankDebitMessageWithRewardPoints() {
        val body = "Rs 1,500.00 debited from A/C XX4321 on 15-OCT-26 at BOOKMYSHOW. UPI Ref: 123456. Avl Bal: Rs 45,210.00. Earn 50 reward points on next spend."
        val category = CategoryClassifier.classify("HDFCBK", body)
        assertEquals(SmsCategory.FINANCE, category)
    }

    @Test
    fun testOtpMessageForTicketBooking() {
        val body = "948201 is your OTP for booking tickets on BookMyShow. Valid for 10 minutes. Do not share this code with anyone."
        val category = CategoryClassifier.classify("VK-BMSHOW", body)
        assertEquals(SmsCategory.OTP, category)
    }

    @Test
    fun testPromotionalDiscountFromBookMyShow() {
        val body = "BookMyShow: Flat 20% discount on movie tickets this weekend! Use promocode WEEKEND20 on checkout. Book now on app."
        val category = CategoryClassifier.classify("VK-BMSHOW", body)
        assertEquals(SmsCategory.PROMOTIONAL, category)
    }

    @Test
    fun testRedBusPromoDiscount() {
        val body = "RedBus: Festive season sale! Get flat 15% discount on all bus tickets with coupon BUSFEST. Book now on redbus app."
        val category = CategoryClassifier.classify("VM-REDBUS", body)
        assertEquals(SmsCategory.PROMOTIONAL, category)
    }

    @Test
    fun testExplicitlyNotSpamOverridesScamSpamToPersonal() {
        val body = "Congratulations! You won 10,000,000 cash prize in UK lucky draw! Claim your prize now at http://scam.link"
        val category = CategoryClassifier.classify("919988776655", body, isExplicitlyNotSpam = true)
        assertEquals(SmsCategory.PERSONAL, category)
    }

    @Test
    fun testExplicitlyNotSpamOverridesGibberishSpamToPersonal() {
        val body = "asdfghjkl qwertyuiop zxcvbnm"
        val category = CategoryClassifier.classify("919988776655", body, isExplicitlyNotSpam = true)
        assertEquals(SmsCategory.PERSONAL, category)
    }

    @Test
    fun testExplicitlyNotSpamClassifiesToTicketsWhenBookingContentPresent() {
        val body = "PNR: 4234567890, Train: 12626 / KERALA EXPRESS, bit.ly/track Tatkal booked confirmed"
        val category = CategoryClassifier.classify("919988776655", body, isExplicitlyNotSpam = true)
        assertEquals(SmsCategory.TICKETS, category)
    }

    @Test
    fun testExplicitlyNotSpamClassifiesToFinanceWhenTransactionPresent() {
        val body = "Rs 2500 debited from acct XX1234 upi ref 998877 avl bal 10000 bit.ly/bank"
        val category = CategoryClassifier.classify("919988776655", body, isExplicitlyNotSpam = true)
        assertEquals(SmsCategory.FINANCE, category)
    }
}
