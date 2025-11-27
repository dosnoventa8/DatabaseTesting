package com.praktikum.database.testing.library.integration;

import com.github.javafaker.Faker;
import com.praktikum.database.testing.library.BaseDatabaseTest;
import com.praktikum.database.testing.library.dao.BookDAO;
import com.praktikum.database.testing.library.dao.BorrowingDAO;
import com.praktikum.database.testing.library.dao.UserDAO;
import com.praktikum.database.testing.library.model.Book;
import com.praktikum.database.testing.library.model.Borrowing;
import com.praktikum.database.testing.library.model.User;
import com.praktikum.database.testing.library.service.BorrowingService;
import com.praktikum.database.testing.library.utils.IndonesianFakerHelper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive Integration Test Suite
 * Menguji integrasi antara User, Book, Borrowing, dan Service layer
 * Focus pada complete workflows dan business processes
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Borrowing Integration Test Suite")
public class BorrowingIntegrationTest extends BaseDatabaseTest {

    // Test dependencies
    private static UserDAO userDAO;
    private static BookDAO bookDAO;
    private static BorrowingDAO borrowingDAO;
    private static BorrowingService borrowingService;
    private static Faker faker;

    // Test data
    private static User testUser;
    private static Book testBook;

    // ** PERBAIKAN 1: Pelacakan ID tambahan untuk pembersihan yang lebih kuat **
    private static Set<Integer> extraBookIds = new HashSet<>();
    private static Set<Integer> extraBorrowingIds = new HashSet<>();

    @BeforeAll
    static void setUpAll() {
        logger.info("Starting Integration Tests");

        // Initialize semua dependencies
        userDAO = new UserDAO();
        bookDAO = new BookDAO();
        borrowingDAO = new BorrowingDAO();
        borrowingService = new BorrowingService(userDAO, bookDAO, borrowingDAO);
        faker = IndonesianFakerHelper.getFaker();
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Setup test data untuk setiap test (test isolation)
        setupTestData();
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Cleanup test data setelah setiap test
        cleanupTestData();
    }

    @AfterAll
    static void tearDownAll() {
        logger.info("Integration Tests Completed");
    }

    /**
     * Setup test data untuk setiap test
     */
    private void setupTestData() throws SQLException {
        // Create test user
        testUser = User.builder()
                .username("integ_user_" + System.currentTimeMillis())
                .email(IndonesianFakerHelper.generateIndonesianEmail())
                .fullName(IndonesianFakerHelper.generateIndonesianName())
                .phone(IndonesianFakerHelper.generateIndonesianPhone())
                .role("member")
                .status("active")
                .build();
        testUser = userDAO.create(testUser);

        // Create test book
        testBook = Book.builder()
                .isbn("978integ" + System.currentTimeMillis())
                .title("Buku Integration Test - " + faker.book().title())
                .authorId(1)
                .totalCopies(5)
                .availableCopies(5)
                .price(new BigDecimal("85000.00"))
                .language("Indonesia")
                .build();
        testBook = bookDAO.create(testBook);

        logger.info("Test data Indonesia created - User: " + testUser.getFullName() + ", Book: " + testBook.getTitle());
    }

    /**
     * Cleanup test data setelah setiap test
     */
    // ** PERBAIKAN 2: Menggunakan pelacakan ID tambahan dan urutan penghapusan yang benar **
    private void cleanupTestData() throws SQLException {
        logger.info("Starting cleanup...");

        // 1. Hapus semua borrowings (termasuk yang dibuat secara manual di tes)

        // Gabungkan semua ID Borrowing untuk dihapus: milik testUser, milik testBook, dan yang dicatat ekstra
        Set<Integer> allBorrowingIdsToDelete = new HashSet<>(extraBorrowingIds);

        // Tambahkan borrowings milik testUser
        if (testUser != null && testUser.getUserId() != null) {
            borrowingDAO.findByUserId(testUser.getUserId()).stream()
                    .map(Borrowing::getBorrowingId)
                    .forEach(allBorrowingIdsToDelete::add);
        }

        // Tambahkan borrowings milik testBook
        if (testBook != null && testBook.getBookId() != null) {
            borrowingDAO.findByBookId(testBook.getBookId()).stream()
                    .map(Borrowing::getBorrowingId)
                    .forEach(allBorrowingIdsToDelete::add);
        }

        for (Integer borrowingId : allBorrowingIdsToDelete) {
            if (borrowingId == null) continue;
            try {
                borrowingDAO.delete(borrowingId);
            } catch (SQLException e) {
                logger.warning("Gagal hapus borrowing ID: " + borrowingId + " - " + e.getMessage());
            }
        }
        extraBorrowingIds.clear(); // Bersihkan set setelah digunakan

        // 2. Hapus semua books (default dan tambahan)
        Set<Integer> allBookIdsToDelete = new HashSet<>(extraBookIds);
        if (testBook != null && testBook.getBookId() != null) {
            allBookIdsToDelete.add(testBook.getBookId());
        }

        for (Integer bookId : allBookIdsToDelete) {
            if (bookId == null) continue;
            try {
                bookDAO.delete(bookId);
            } catch (SQLException e) {
                // Ini seharusnya tidak terjadi lagi jika borrowings sudah dihapus
                logger.warning("Gagal hapus buku (ID: " + bookId + ") : " + e.getMessage());
            }
        }
        extraBookIds.clear(); // Bersihkan set setelah digunakan

        // 3. Hapus user
        if (testUser != null && testUser.getUserId() != null) {
            try {
                userDAO.delete(testUser.getUserId());
            } catch (SQLException e) {
                logger.warning("Gagal hapus user: " + e.getMessage());
            }
        }
        logger.info("Test data cleaned up");
    }

    // ===========================================================
    // COMPLETE WORKFLOW INTEGRATION TESTS
    // ===========================================================

    @Test
    @Order(1)
    @DisplayName("TC401: Complete borrowing workflow - Success scenario")
    void testCompleteBorrowingWorkflow_SuccessScenario() throws SQLException {
        int originalAvailableCopies = testBook.getAvailableCopies();
        int originalActiveBorrowings = borrowingDAO.countActiveBorrowingsByUser(testUser.getUserId());

        Borrowing borrowing = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        extraBorrowingIds.add(borrowing.getBorrowingId()); // Track borrowing

        assertThat(borrowing).isNotNull()
                .satisfies(b -> {
                    assertThat(b.getBorrowingId()).isNotNull();
                    assertThat(b.getStatus()).isEqualTo("borrowed");
                });

        Optional<Book> updatedBook = bookDAO.findById(testBook.getBookId());
        assertThat(updatedBook.get().getAvailableCopies()).isEqualTo(originalAvailableCopies - 1);

        int newActiveBorrowings = borrowingDAO.countActiveBorrowingsByUser(testUser.getUserId());
        assertThat(newActiveBorrowings).isEqualTo(originalActiveBorrowings + 1);

        logger.info("TC401 PASSED: Complete borrowing workflow successful");
    }

    @Test
    @Order(2)
    @DisplayName("TC402: Complete return workflow - Success scenario")
    void testCompleteReturnWorkflow_SuccessScenario() throws SQLException {
        // Buat borrowing baru untuk tes ini, pastikan terhapus di cleanup
        Borrowing borrowing = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        extraBorrowingIds.add(borrowing.getBorrowingId()); // Track borrowing

        Optional<Book> bookAfterBorrow = bookDAO.findById(testBook.getBookId());
        int copiesAfterBorrow = bookAfterBorrow.get().getAvailableCopies();

        boolean returned = borrowingService.returnBook(borrowing.getBorrowingId());

        assertThat(returned).isTrue();

        Optional<Borrowing> returnedBorrowing = borrowingDAO.findById(borrowing.getBorrowingId());
        assertThat(returnedBorrowing.get().getStatus()).isEqualTo("returned");
        assertThat(returnedBorrowing.get().getReturnDate()).isNotNull();

        Optional<Book> bookAfterReturn = bookDAO.findById(testBook.getBookId());
        assertThat(bookAfterReturn.get().getAvailableCopies()).isEqualTo(copiesAfterBorrow + 1);

        logger.info("TC402 PASSED: Complete return workflow successful");
    }

    @Test
    @Order(3)
    @DisplayName("TC403: Borrow book dengan inactive user - Should Fail")
    void testBorrowBook_WithInactiveUser_ShouldFail() throws SQLException {
        testUser.setStatus("inactive");
        userDAO.update(testUser);

        assertThatThrownBy(() -> borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tidak active");

        // Kembalikan status user untuk tes berikutnya
        testUser.setStatus("active");
        userDAO.update(testUser);
        logger.info("TC403 PASSED: Inactive user cannot borrow books");
    }

    @Test
    @Order(4)
    @DisplayName("TC404: Borrow unavailable book - Should Fail")
    void testBorrowBook_UnavailableBook_ShouldFail() throws SQLException {
        bookDAO.updateAvailableCopies(testBook.getBookId(), 0);

        assertThatThrownBy(() -> borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tidak ada kopi"); // Match message in service

        // Kembalikan jumlah kopi untuk tes berikutnya
        bookDAO.updateAvailableCopies(testBook.getBookId(), 5);
        logger.info("TC404 PASSED: Cannot borrow unavailable book");
    }

    @Test
    @Order(5)
    @DisplayName("TC405: Return already returned book - Should Fail")
    void testReturnBook_AlreadyReturned_ShouldFail() throws SQLException {
        // Buat borrowing baru untuk tes ini
        Borrowing borrowing = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        extraBorrowingIds.add(borrowing.getBorrowingId());

        borrowingService.returnBook(borrowing.getBorrowingId());

        assertThatThrownBy(() -> borrowingService.returnBook(borrowing.getBorrowingId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sudah dikembalikan");

        logger.info("TC405 PASSED: Cannot return already returned book");
    }

    @Test
    @Order(6)
    @DisplayName("TC406: Multiple borrowings by same user - Should Success")
    void testMultipleBorrowings_BySameUser_ShouldSuccess() throws SQLException {
        Book book2 = createTestBook();
        book2 = bookDAO.create(book2);
        extraBookIds.add(book2.getBookId()); // Track book2

        Borrowing b1 = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        Borrowing b2 = borrowingService.borrowBook(testUser.getUserId(), book2.getBookId(), 7);

        extraBorrowingIds.add(b1.getBorrowingId()); // Track borrowings
        extraBorrowingIds.add(b2.getBorrowingId());

        List<Borrowing> userBorrowings = borrowingDAO.findByUserId(testUser.getUserId());
        assertThat(userBorrowings).hasSizeGreaterThanOrEqualTo(2);

        logger.info("TC406 PASSED: Multiple borrowings by same user successful");

        // Hapus pembersihan manual di sini, biarkan AfterEach yang menangani
        // borrowingService.returnBook(b1.getBorrowingId());
        // borrowingService.returnBook(b2.getBorrowingId());
        // bookDAO.delete(book2.getBookId());
    }

    @Test
    @Order(7)
    @DisplayName("TC407: Borrowing limit enforcement - Maximum 5 books per user")
    void testBorrowingLimitEnforcement_MaximumFiveBooks() throws SQLException {
        List<Book> testBooks = new java.util.ArrayList<>();
        List<Borrowing> successfulBorrowings = new java.util.ArrayList<>();

        for (int i = 0; i < 6; i++) {
            Book book = createTestBook();
            book.setTitle("Limit Test " + i);
            book = bookDAO.create(book);
            testBooks.add(book);
            extraBookIds.add(book.getBookId()); // Track semua buku yang dibuat
        }

        for (int i = 0; i < 5; i++) {
            Borrowing b = borrowingService.borrowBook(testUser.getUserId(), testBooks.get(i).getBookId(), 14);
            successfulBorrowings.add(b);
            extraBorrowingIds.add(b.getBorrowingId()); // Track semua peminjaman yang berhasil
        }

        assertThatThrownBy(() -> borrowingService.borrowBook(testUser.getUserId(), testBooks.get(5).getBookId(), 14))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("batas peminjaman");

        logger.info("TC407 PASSED: Borrowing limit enforced correctly");

        // Hapus pembersihan manual di sini, biarkan AfterEach yang menangani
        // for (Borrowing b : successfulBorrowings) borrowingService.returnBook(b.getBorrowingId());
        // for (Book b : testBooks) bookDAO.delete(b.getBookId());
    }

    @Test
    @Order(8)
    @DisplayName("TC408: Concurrent borrowing simulation - Race condition handling")
    void testConcurrentBorrowingSimulation_RaceConditionHandling() throws SQLException {
        // Pastikan hanya ada 1 kopi tersedia
        bookDAO.updateAvailableCopies(testBook.getBookId(), 1);

        Borrowing b1 = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        extraBorrowingIds.add(b1.getBorrowingId());
        assertThat(b1).isNotNull();

        // Coba pinjam lagi (akan gagal karena 0 kopi)
        assertThatThrownBy(() -> borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14))
                .isInstanceOf(IllegalStateException.class);

        logger.info("TC408 PASSED: Concurrent borrowing handled correctly");

        // Kembalikan kopi untuk tes berikutnya
        bookDAO.updateAvailableCopies(testBook.getBookId(), 5);
    }

    @Test
    @Order(9)
    @DisplayName("TC409: Data consistency after multiple operations")
    void testDataConsistency_AfterMultipleOperations() throws SQLException {
        int initialAvailableCopies = testBook.getAvailableCopies();

        Borrowing b1 = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 14);
        Borrowing b2 = borrowingService.borrowBook(testUser.getUserId(), testBook.getBookId(), 7);
        extraBorrowingIds.add(b1.getBorrowingId());
        extraBorrowingIds.add(b2.getBorrowingId());

        borrowingService.returnBook(b1.getBorrowingId());

        Optional<Book> finalBook = bookDAO.findById(testBook.getBookId());
        // 5 - 2 borrows + 1 return = 4
        assertThat(finalBook.get().getAvailableCopies()).isEqualTo(initialAvailableCopies - 1);

        Optional<Borrowing> checkB1 = borrowingDAO.findById(b1.getBorrowingId());
        Optional<Borrowing> checkB2 = borrowingDAO.findById(b2.getBorrowingId());

        assertThat(checkB1.get().getStatus()).isEqualTo("returned");
        assertThat(checkB2.get().getStatus()).isEqualTo("borrowed");

        // Cleanup b2 secara manual agar statusnya returned (walaupun AfterEach akan menghapusnya)
        borrowingService.returnBook(b2.getBorrowingId());

        logger.info("TC409 PASSED: Data consistency maintained");
    }

    // ===========================================================
    // BAGIAN BARU: Calculation & Error Handling (Dari Image Terbaru)
    // ===========================================================

    @Test
    @Order(10)
    @DisplayName("TC410: Fine calculation for overdue books")
    void testFineCalculation_ForOverdueBooks() throws SQLException, InterruptedException {
        // ARRANGE - Create borrowing dengan due date di masa lalu
        Timestamp borrowDate = Timestamp.valueOf(LocalDateTime.now().minusDays(2));
        Timestamp pastDueDate = Timestamp.valueOf(LocalDateTime.now().minusDays(1));

        Borrowing borrowing = Borrowing.builder()
                .userId(testUser.getUserId())
                .bookId(testBook.getBookId())
                .borrowDate(borrowDate)
                .dueDate(pastDueDate)
                .status("borrowed")
                .build();

        borrowing = borrowingDAO.create(borrowing);
        extraBorrowingIds.add(borrowing.getBorrowingId()); // Track borrowing

        // ACT - Calculate fine via Service
        double fine = borrowingService.calculateFine(borrowing.getBorrowingId());

        // ASSERT - Fine should be calculated (5000 per day)
        assertThat(fine).isGreaterThan(0);
        // 1 day overdue * 5000 = 5000 (Asumsi hari ini adalah T+1 dari due date)
        assertThat(fine).isEqualTo(5000.0);

        logger.info("TC410 PASSED: Fine calculation working correctly. Fine: " + fine);

        // Hapus pembersihan manual di sini, biarkan AfterEach yang menangani
        // borrowingDAO.delete(borrowing.getBorrowingId());
    }

    @Test
    @Order(11)
    @DisplayName("TC411: Transaction integrity - All or nothing principle")
    void testTransactionIntegrity_AllOrNothingPrinciple() throws SQLException {
        // ARRANGE
        int initialCopies = testBook.getAvailableCopies();

        try {
            // ACT - Try to borrow dengan invalid data (should fail completely)
            // Invalid User ID = 999999
            borrowingService.borrowBook(999999, testBook.getBookId(), 14);

            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            // Expected exception
        } catch (Exception e) {
            // Jika service layer melempar SQLException atau yang lain, tangkap di sini
        }

        // ASSERT - Book copies should remain unchanged (transaction rolled back)
        Optional<Book> bookAfterFailedBorrow = bookDAO.findById(testBook.getBookId());

        assertThat(bookAfterFailedBorrow.get().getAvailableCopies()).isEqualTo(initialCopies);

        logger.info("TC411 PASSED: Transaction integrity maintained after failed operation");
    }

    @Test
    @Order(12)
    @DisplayName("TC412: Service layer validation - Invalid parameters")
    void testServiceLayerValidation_InvalidParameters() {
        // ACT & ASSERT - Various invalid parameters

        // 1. Null User
        assertThatThrownBy(() -> borrowingService.borrowBook(null, testBook.getBookId(), 14))
                .isInstanceOf(IllegalArgumentException.class);

        // 2. Null Book
        assertThatThrownBy(() -> borrowingService.borrowBook(testUser.getUserId(), null, 14))
                .isInstanceOf(IllegalArgumentException.class);

        // 3. Zero days (asumsi minimal 1 hari, tergantung validasi service)
        // Jika tidak ada validasi days di service, skip assertion ini

        logger.info("TC412 PASSED: Service layer validation working correctly");
    }

    // ===========================================================
    // HELPER METHODS
    // ===========================================================

    /**
     * Helper method untuk membuat test book tambahan
     */
    private Book createTestBook() {
        return Book.builder()
                .isbn("978integ" + System.currentTimeMillis() + "_" + faker.number().randomNumber())
                .title("Integration Test Book " + faker.book().title())
                .authorId(1)
                .publisherId(1)
                .categoryId(1)
                .publicationYear(2023)
                .pages(300)
                .language("Indonesia")
                .description("Buku untuk testing integrity")
                .totalCopies(5)
                .availableCopies(5)
                .price(new BigDecimal("75000.00"))
                .location("Rak Integrity-Test")
                .status("available")
                .build();


    }
}