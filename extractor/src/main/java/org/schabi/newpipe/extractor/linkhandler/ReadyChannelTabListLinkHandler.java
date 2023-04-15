package org.schabi.newpipe.extractor.linkhandler;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelTabExtractor;

import java.util.Collections;

/**
 * Can be used as a {@link ListLinkHandler} to be returned from {@link
 * org.schabi.newpipe.extractor.channel.ChannelInfo#getTabs()} when a specific tab's data has
 * already been fetched. This class allows passing a {@link ChannelTabExtractor} that
 * can hold references to variables.
 * <p>
 * Note: a service that wishes to use this class in one of its {@link
 * org.schabi.newpipe.extractor.channel.ChannelExtractor}s must also add the following snippet of
 * code in the service's {@link StreamingService#getChannelTabExtractor(ListLinkHandler)}:
 * <pre>
 * if (linkHandler instanceof ReadyChannelTabListLinkHandler) {
 *     return ((ReadyChannelTabListLinkHandler) linkHandler).getChannelTabExtractor();
 * }
 * </pre>
 */
public class ReadyChannelTabListLinkHandler extends ListLinkHandler {

    private final ChannelTabExtractor extractor;

    public ReadyChannelTabListLinkHandler(final String url,
                                          final String channelId,
                                          final String channelTab,
                                          final ChannelTabExtractor extractor) {
        super(url, url, channelId, Collections.singletonList(channelTab), "");
        this.extractor = extractor;
    }

    public ChannelTabExtractor getChannelTabExtractor() {
        return extractor;
    }
}
