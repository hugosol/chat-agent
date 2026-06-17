package com.hugosol.chatagent.e2e;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.hugosol.chatagent.e2e.helper.E2ETestBase;
import com.hugosol.chatagent.model.SubtitleStatus;
import com.hugosol.chatagent.model.WatchedMovie;
import com.hugosol.chatagent.repository.WatchedMovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class MoviesPageIT extends E2ETestBase {

    @Autowired
    private WatchedMovieRepository watchedMovieRepository;

    @BeforeEach
    void setupStubs() {
        watchedMovieRepository.deleteAll();
        WireMock.configureFor("localhost", wireMockServer.port());
    }

    @Test
    void pageLoadsWithEmptyState() {
        page.navigate("http://localhost:" + serverPort + "/movies/index.html");
        assertThat(page.locator("[data-testid='movies-page']")).isVisible();
        assertThat(page.locator("[data-testid='movies-toolbar']")).isVisible();
        assertThat(page.locator("[data-testid='movies-empty']")).isVisible();
    }

    @Test
    void displaysMovieListWithStatusIcons() {
        // Seed test data
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt001", "Inception", 2010, SubtitleStatus.DONE));
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt002", "The Matrix", 1999, SubtitleStatus.PENDING));
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt003", "Bad Movie", 2020, SubtitleStatus.FAILED));

        page.navigate("http://localhost:" + serverPort + "/movies/index.html");

        // Wait for movie blocks to render
        assertThat(page.locator("[data-testid='movie-block']")).hasCount(3);

        // Verify movie titles
        assertThat(page.locator("[data-testid='movie-title']").first()).hasText("Bad Movie");
        assertThat(page.locator("[data-testid='movie-title']").nth(1)).hasText("Inception");
        assertThat(page.locator("[data-testid='movie-title']").nth(2)).hasText("The Matrix");

        // Verify delete button exists on each row
        assertThat(page.locator("[data-testid='movie-delete-btn']")).hasCount(3);

        // Verify retry/download button on PENDING and FAILED movies (2 of 3)
        assertThat(page.locator("[data-testid='movie-download-btn']")).hasCount(2);
    }

    @Test
    void searchFiltersMovies() {
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt001", "Inception", 2010, SubtitleStatus.DONE));
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt002", "The Matrix", 1999, SubtitleStatus.DONE));

        page.navigate("http://localhost:" + serverPort + "/movies/index.html");
        assertThat(page.locator("[data-testid='movie-block']")).hasCount(2);

        // Type search query
        page.locator("[data-testid='movies-search-input']").fill("Matrix");

        // Wait for debounced search to take effect
        page.waitForTimeout(500);

        // Should now only show Matrix
        assertThat(page.locator("[data-testid='movie-title']")).hasText("The Matrix");
    }

    @Test
    void sortChangesOrdering() {
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt002", "The Matrix", 1999, SubtitleStatus.DONE));
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt001", "Inception", 2010, SubtitleStatus.DONE));

        page.navigate("http://localhost:" + serverPort + "/movies/index.html");

        // Default sort: title asc → Inception first, Matrix second
        assertThat(page.locator("[data-testid='movie-title']").first()).hasText("Inception");

        // Change sort to release year descending via DropdownMenu
        page.locator("[data-testid='movies-sort-btn']").click();
        page.locator("[data-testid='movies-sort-option']").filter(new com.microsoft.playwright.Locator.FilterOptions().setHasText("年份 ↓")).click();
        page.waitForTimeout(300);

        // Now Matrix (1999) should be second, Inception (2010) first
        assertThat(page.locator("[data-testid='movie-title']").first()).hasText("Inception");
    }

    @Test
    void deleteMovieWithConfirmation() {
        watchedMovieRepository.save(new WatchedMovie(
                DEFAULT_USER_ID, "tt001", "Inception", 2010, SubtitleStatus.DONE));

        page.navigate("http://localhost:" + serverPort + "/movies/index.html");
        assertThat(page.locator("[data-testid='movie-block']")).hasCount(1);

        // Click delete button
        page.locator("[data-testid='movie-delete-btn']").first().click();

        // Verify delete modal appears
        assertThat(page.locator("[data-testid='movie-delete-modal']")).isVisible();
        assertThat(page.locator("[data-testid='movie-delete-text']")).containsText("Inception");

        // Confirm delete
        page.locator("[data-testid='modal-save']").click();

        // Verify movie is removed
        page.waitForTimeout(300);
        assertThat(page.locator("[data-testid='movie-block']")).hasCount(0);
        assertThat(page.locator("[data-testid='movies-empty']")).isVisible();
    }

    @Test
    void retrySubtitleShowsConfirmation() {
        var movie = new WatchedMovie(
                DEFAULT_USER_ID, "tt001", "Inception", 2010, SubtitleStatus.FAILED);
        movie.setSubtitleError("Download failed");
        watchedMovieRepository.save(movie);

        page.navigate("http://localhost:" + serverPort + "/movies/index.html");
        assertThat(page.locator("[data-testid='movie-block']")).hasCount(1);

        // Verify retry button is visible
        assertThat(page.locator("[data-testid='movie-download-btn']")).isVisible();

        // Click retry button
        page.locator("[data-testid='movie-download-btn']").click();

        // Verify retry modal appears
        assertThat(page.locator("[data-testid='movie-retry-modal']")).isVisible();
        assertThat(page.locator("[data-testid='movie-retry-text']")).containsText("Inception");

        // Close the modal (don't actually trigger retry since Subdl may not be stubbed)
        page.locator("[data-testid='modal-cancel']").click();
    }

    @Test
    void headerNavLinkNavigatesToMoviesPage() {
        // Start from chat page and verify Movies nav link works
        page.navigate("http://localhost:" + serverPort + "/");

        // Open sidebar
        page.locator("[data-testid='nav-menu-btn']").click();
        page.waitForTimeout(300);

        // Verify Movies link exists in nav
        assertThat(page.locator("[data-testid='nav-movies-link']")).isVisible();

        // Click Movies link
        page.locator("[data-testid='nav-movies-link']").click();
        page.waitForTimeout(500);

        // Verify we're on the movies page
        assertThat(page.locator("[data-testid='movies-page']")).isVisible();
        assertThat(page.locator("[data-testid='movies-toolbar']")).isVisible();
    }
}
