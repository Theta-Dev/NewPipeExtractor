package org.schabi.newpipe.extractor.services.peertube.extractors;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.ChannelTabHandler;
import org.schabi.newpipe.extractor.services.peertube.PeertubeParsingHelper;
import org.schabi.newpipe.extractor.services.peertube.linkHandler.PeertubeChannelLinkHandlerFactory;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nonnull;
import java.io.IOException;

import static org.schabi.newpipe.extractor.services.peertube.PeertubeParsingHelper.COUNT_KEY;
import static org.schabi.newpipe.extractor.services.peertube.PeertubeParsingHelper.ITEMS_PER_PAGE;
import static org.schabi.newpipe.extractor.services.peertube.PeertubeParsingHelper.START_KEY;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class PeertubeChannelTabExtractor extends ChannelTabExtractor {
    private final String baseUrl;

    public PeertubeChannelTabExtractor(final StreamingService service,
                                       final ChannelTabHandler linkHandler)
            throws ParsingException {
        super(service, linkHandler);
        baseUrl = getBaseUrl();
    }

    @Override
    public void onFetchPage(final @Nonnull Downloader downloader) {
        if (getLinkHandler().getTab() != ChannelTabHandler.Tab.Playlists) {
            throw new IllegalArgumentException(
                    "tab " + getLinkHandler().getTab().name() + " not supported");
        }
    }

    @Nonnull
    @Override
    public InfoItemsPage<InfoItem> getInitialPage()
            throws IOException, ExtractionException {
        return getPage(new Page(baseUrl + PeertubeChannelLinkHandlerFactory.API_ENDPOINT + getId()
                + "/video-playlists?" + START_KEY + "=0&" + COUNT_KEY + "=" + ITEMS_PER_PAGE));
    }

    @Override
    public InfoItemsPage<InfoItem> getPage(final Page page)
            throws IOException, ExtractionException {
        if (page == null || isNullOrEmpty(page.getUrl())) {
            throw new IllegalArgumentException("Page doesn't contain an URL");
        }

        final Response response = getDownloader().get(page.getUrl());

        JsonObject pageJson = null;
        if (response != null && !Utils.isBlank(response.responseBody())) {
            try {
                pageJson = JsonParser.object().from(response.responseBody());
            } catch (final Exception e) {
                throw new ParsingException("Could not parse json data for account info", e);
            }
        }

        if (pageJson == null) {
            throw new ExtractionException("Unable to get channel playlist list");
        }

        PeertubeParsingHelper.validate(pageJson);

        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(getServiceId());
        final JsonArray contents = pageJson.getArray("data");
        if (contents == null) {
            throw new ParsingException("Unable to extract channel playlist list");
        }

        for (final Object c : contents) {
            if (c instanceof JsonObject) {
                collector.commit(new PeertubePlaylistInfoItemExtractor((JsonObject) c, baseUrl));
            }
        }

        return new InfoItemsPage<>(
                collector, PeertubeParsingHelper.getNextPage(page.getUrl(),
                pageJson.getLong("total")));
    }
}
