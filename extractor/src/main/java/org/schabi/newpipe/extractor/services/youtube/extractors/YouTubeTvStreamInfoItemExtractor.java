package org.schabi.newpipe.extractor.services.youtube.extractors;

import com.grack.nanojson.JsonObject;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.localization.TimeAgoParser;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.utils.Utils;

import javax.annotation.Nullable;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getThumbnailUrlFromInfoItem;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.getTextFromObject;
import static org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.parseDurationString;
import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public class YouTubeTvStreamInfoItemExtractor implements StreamInfoItemExtractor {
    private static final String NO_VIEWS_LOWERCASE = "no views";

    private final JsonObject videoInfo;
    private final JsonObject metadata;
    private final JsonObject header;
    private final TimeAgoParser timeAgoParser;

    public YouTubeTvStreamInfoItemExtractor(final JsonObject videoInfoItem,
                                            @Nullable final TimeAgoParser timeAgoParser) {
        this.videoInfo = videoInfoItem;
        this.timeAgoParser = timeAgoParser;

        metadata = videoInfo.getObject("metadata").getObject("tileMetadataRenderer");
        header = videoInfo.getObject("header").getObject("tileHeaderRenderer");
    }

    @Override
    public String getName() throws ParsingException {
        return getTextFromObject(metadata.getObject("title"));
    }

    @Override
    public String getUrl() throws ParsingException {
        try {
            final String videoId = videoInfo.getString("contentId");
            return YoutubeStreamLinkHandlerFactory.getInstance().getUrl(videoId);
        } catch (final Exception e) {
            throw new ParsingException("Could not get url", e);
        }
    }

    @Override
    public String getThumbnailUrl() throws ParsingException {
        return getThumbnailUrlFromInfoItem(header);
    }

    @Override
    public StreamType getStreamType() throws ParsingException {
        for (final Object overlay : header.getArray("thumbnailOverlays")) {
            if (!(overlay instanceof JsonObject)) {
                continue;
            }

            final String style = ((JsonObject) overlay)
                    .getObject("thumbnailOverlayTimeStatusRenderer")
                    .getString("style", "");
            if (style.equalsIgnoreCase("LIVE")) {
                return StreamType.LIVE_STREAM;
            }
        }
        return StreamType.VIDEO_STREAM;
    }

    @Override
    public boolean isAd() throws ParsingException {
        return getName().equals("[Private video]")
                || getName().equals("[Deleted video]");
    }

    @Override
    public long getDuration() throws ParsingException {
        if (getStreamType() == StreamType.LIVE_STREAM) {
            return -1;
        }

        final JsonObject timeOverlay = header.getArray("thumbnailOverlays")
                .stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .filter(thumbnailOverlay ->
                        thumbnailOverlay.has("thumbnailOverlayTimeStatusRenderer"))
                .findFirst()
                .orElse(null);

        if (timeOverlay != null) {
            final String duration = getTextFromObject(
                    timeOverlay.getObject("thumbnailOverlayTimeStatusRenderer")
                            .getObject("text"));
            if (!isNullOrEmpty(duration)) {
                return parseDurationString(duration);
            }
        }

        throw new ParsingException("Could not get duration");
    }

    private boolean isPremiere() {
        for (final Object overlay : header.getArray("thumbnailOverlays")) {
            if (!(overlay instanceof JsonObject)) {
                continue;
            }

            final String style = ((JsonObject) overlay)
                    .getObject("thumbnailOverlayTimeStatusRenderer")
                    .getString("style", "");
            if (style.equalsIgnoreCase("UPCOMING")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getViewCount() throws ParsingException {
        final Optional<Object> itm = metadata.getArray("lines")
                .getObject(1)
                .getObject("lineRenderer")
                .getArray("items")
                .stream()
                .filter(item -> item instanceof JsonObject
                        && ((JsonObject) item).getObject("lineItemRenderer").has("text"))
                .findFirst();
        if (itm.isPresent()) {
            final JsonObject lineItem = (JsonObject) itm.get();
            final String viewCountText =
                    getTextFromObject(lineItem.getObject("lineItemRenderer").getObject("text"));
            if (!isNullOrEmpty(viewCountText)) {
                if (viewCountText.toLowerCase().contains(NO_VIEWS_LOWERCASE)) {
                    return 0;
                }
                return Utils.mixedNumberWordToLong(viewCountText);
            }
        }

        return 0;
    }

    @Override
    public String getUploaderName() throws ParsingException {
        throw new ParsingException("Could not get uploader name");
    }

    @Override
    public String getUploaderUrl() throws ParsingException {
        throw new ParsingException("Could not get uploader url");
    }

    @Nullable
    @Override
    public String getUploaderAvatarUrl() throws ParsingException {
        return null;
    }

    @Override
    public boolean isUploaderVerified() throws ParsingException {
        return false;
    }

    @Nullable
    @Override
    public String getTextualUploadDate() throws ParsingException {
        final int dateIndex = isPremiere() ? 0 : 2;
        final Optional<Object> itm = metadata.getArray("lines")
                .getObject(1)
                .getObject("lineRenderer")
                .getArray("items")
                .stream()
                .filter(item -> item instanceof JsonObject
                        && ((JsonObject) item).getObject("lineItemRenderer").has("text"))
                .skip(dateIndex)
                .findFirst();

        if (itm.isPresent()) {
            final JsonObject lineItem = (JsonObject) itm.get();
            return getTextFromObject(lineItem.getObject("lineItemRenderer").getObject("text"));
        }
        return null;
    }

    /**
     * Parset datetimes in the following format:
     * <p>
     * "Scheduled for 14/04/2023, 15:00"
     *
     * @param textualDate textual date
     * @return parsed date
     * @throws ParsingException if an error occurred
     */
    private DateWrapper parseDatetime(final String textualDate) throws ParsingException {
        // Find the first digit in the input string
        for (int i = 0; i < textualDate.length(); i++) {
            if (Character.isDigit(textualDate.charAt(i))) {
                final String substr = textualDate.substring(i);
                final DateTimeFormatter parser = DateTimeFormatter.ofPattern("MM/dd/yyyy, HH:mm");
                try {
                    return new DateWrapper(OffsetDateTime.of(
                            LocalDateTime.parse(substr, parser), ZoneOffset.UTC
                    ));
                } catch (final DateTimeParseException exception) {
                    break;
                }
            }
        }
        throw new ParsingException(
                "Could not parse date from premiere: \"" + textualDate + "\"");
    }

    @Nullable
    @Override
    public DateWrapper getUploadDate() throws ParsingException {
        final String textualUploadDate = getTextualUploadDate();

        if (isNullOrEmpty(textualUploadDate)) {
            return null;
        } else if (isPremiere()) {
            return parseDatetime(textualUploadDate);
        } else {
            return timeAgoParser.parse(textualUploadDate);
        }
    }
}
