package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonWriter;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBE_TV_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.YOUTUBEI_V1_URL;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.DISABLE_PRETTY_PRINT_PARAMETER;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getYoutubeTvHeaders;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getYoutubeTvKey;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getValidJsonResponseBody;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.defaultAlertsCheck;
import static org.schabi.newpipe.extractor.services.youtube.YouTubeChannelHelper.resolveChannelId;
import static org.schabi.newpipe.extractor.services.youtube.YouTubeChannelHelper.checkIfChannelResponseIsValid;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YouTubeSlowFeedExtractor extends FeedExtractor {
    private JsonObject initialData;
    private String channelId;

    public YouTubeSlowFeedExtractor(final StreamingService service,
                                    final ListLinkHandler listLinkHandler) {
        super(service, listLinkHandler);
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader)
            throws IOException, ExtractionException {
        final String channelPath = super.getId();
        channelId = resolveChannelId(channelPath);

        final String[] youtubeTvKey = getYoutubeTvKey();
        final String url = YOUTUBEI_V1_URL + "browse?key="
                + youtubeTvKey[0] + DISABLE_PRETTY_PRINT_PARAMETER;

        // @formatter:off
        final byte[] json = JsonWriter.string()
            .object()
                .object("context")
                    .object("client")
                        .value("clientName", "TVHTML5")
                        .value("clientVersion", youtubeTvKey[1])
                        .value("deviceModel", "SmartTv")
                        .value("deviceMake", "Samsung")
                        .value("platform", "TV")
                        .value("originalUrl", YOUTUBE_TV_URL)
                        .value("hl", "en-GB")
                        .value("gl", getExtractorContentCountry().getCountryCode())
                    .end()
                    .object("request")
                        .array("internalExperimentFlags").end()
                        .value("useSsl", true)
                    .end()
                    .object("user")
                        .value("lockedSafetyMode", false)
                    .end()
                .end()
                .value("browseId", channelId)
            .end().done().getBytes(StandardCharsets.UTF_8);
        // @formatter:on

        final String responseBody = getValidJsonResponseBody(
                getDownloader().postWithContentTypeJson(url, getYoutubeTvHeaders(), json));

        final JsonObject responseData;
        try {
            responseData = JsonParser.object().from(responseBody);
        } catch (final JsonParserException e) {
            throw new ParsingException("Could not parse JSON", e);
        }

        checkIfChannelResponseIsValid(responseData);
        defaultAlertsCheck(responseData);

        initialData = responseData
                .getObject("contents")
                .getObject("tvBrowseRenderer")
                .getObject("content")
                .getObject("tvSurfaceContentRenderer");
    }

    @Nonnull
    @Override
    public String getId() throws ParsingException {
        return channelId;
    }

    @Nonnull
    @Override
    public String getUrl() throws ParsingException {
        try {
            return YoutubeChannelLinkHandlerFactory.getInstance().getUrl("channel/" + getId());
        } catch (final ParsingException e) {
            return super.getUrl();
        }
    }

    @Nonnull
    @Override
    public String getName() throws ParsingException {
        final String name = getTextFromObject(initialData.getObject("header")
                .getObject("tvSurfaceHeaderRenderer")
                .getObject("title"));
        if (isNullOrEmpty(name)) {
            return "";
        }
        return name;
    }

    @Nonnull
    @Override
    public InfoItemsPage<StreamInfoItem> getInitialPage() throws IOException, ExtractionException {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(getServiceId());

        final Optional<Object> uploadShelf = initialData.getObject("content")
                .getObject("sectionListRenderer")
                .getArray("contents")
                .stream()
                .filter(shelf -> shelf instanceof JsonObject
                        && ((JsonObject) shelf).getObject("shelfRenderer").getObject("endpoint")
                                .getObject("browseEndpoint").getString("params", "")
                                .equals("EgZ2aWRlb3MYAyAAcADyBgsKCToCCAGiAQIIAQ%3D%3D"))
                .findFirst();

        if (uploadShelf.isEmpty()) {
            return InfoItemsPage.emptyPage();
        }

        final String channelName = getName();
        final String channelUrl = getUrl();

        final JsonArray uploads = ((JsonObject) uploadShelf.get())
                .getObject("shelfRenderer")
                .getObject("content")
                .getObject("horizontalListRenderer")
                .getArray("items");

        for (final Object u : uploads) {
            if (!(u instanceof JsonObject)) {
                continue;
            }

            final JsonObject item = ((JsonObject) u).getObject("tileRenderer");
            collector.commit(new YouTubeTvStreamInfoItemExtractor(item, getTimeAgoParser()) {
                @Override
                public String getUploaderName() {
                    return channelName;
                }

                @Override
                public String getUploaderUrl() {
                    return channelUrl;
                }
            });
        }

        return new InfoItemsPage<>(collector, null);
    }

    @Override
    public InfoItemsPage<StreamInfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        return InfoItemsPage.emptyPage();
    }
}
