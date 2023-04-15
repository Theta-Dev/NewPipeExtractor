package org.schabi.newpipe.extractor.services.youtube;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.schabi.newpipe.downloader.DownloaderFactory;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.BaseListExtractorTest;
import org.schabi.newpipe.extractor.services.youtube.extractors.YouTubeSlowFeedExtractor;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.extractor.services.DefaultTests.assertNoMoreItems;
import static org.schabi.newpipe.extractor.services.DefaultTests.defaultTestRelatedItems;

public class YoutubeSlowFeedExtractorTest {

    private static final String RESOURCE_PATH = DownloaderFactory.RESOURCE_PATH + "services/youtube/extractor/feed/";

    public static class Kurzgesagt implements BaseListExtractorTest {
        private static YouTubeSlowFeedExtractor extractor;

        @BeforeAll
        public static void setUp() throws Exception {
            YoutubeTestsUtils.ensureStateless();
            NewPipe.init(DownloaderFactory.getDownloader(RESOURCE_PATH));
            extractor = (YouTubeSlowFeedExtractor) YouTube
                    .getSlowFeedExtractor("https://www.youtube.com/user/Kurzgesagt");
            extractor.fetchPage();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Extractor
        //////////////////////////////////////////////////////////////////////////*/

        @Test
        public void testServiceId() {
            assertEquals(YouTube.getServiceId(), extractor.getServiceId());
        }

        @Test
        public void testName() throws ParsingException {
            assertTrue(extractor.getName().startsWith("Kurzgesagt"));
        }

        @Test
        public void testId() throws ParsingException {
            assertEquals("UCsXVk37bltHxD1rDPwtNM8Q", extractor.getId());
        }

        @Test
        public void testUrl() throws ParsingException {
            assertEquals("https://www.youtube.com/channel/UCsXVk37bltHxD1rDPwtNM8Q", extractor.getUrl());
        }

        @Test
        public void testOriginalUrl() throws ParsingException {
            assertEquals("https://www.youtube.com/user/Kurzgesagt", extractor.getOriginalUrl());
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ListExtractor
        //////////////////////////////////////////////////////////////////////////*/

        @Test
        public void testRelatedItems() throws Exception {
            defaultTestRelatedItems(extractor);
        }

        @Test
        public void testMoreRelatedItems() throws Exception {
            assertNoMoreItems(extractor);
        }
    }

    public static class NotAvailable {

        @BeforeAll
        public static void setUp() throws IOException {
            NewPipe.init(DownloaderFactory.getDownloader(RESOURCE_PATH + "notAvailable/"));
        }

        @Test
        void AccountTerminatedFetch() throws Exception {
            YouTubeSlowFeedExtractor extractor = (YouTubeSlowFeedExtractor) YouTube
                    .getSlowFeedExtractor("https://www.youtube.com/channel/UCTGjY2I-ZUGnwVoWAGRd7XQ");
            assertThrows(ContentNotAvailableException.class, extractor::fetchPage);
        }
    }
}