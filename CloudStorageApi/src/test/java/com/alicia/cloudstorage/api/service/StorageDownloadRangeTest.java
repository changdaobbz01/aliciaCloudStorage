package com.alicia.cloudstorage.api.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageDownloadRangeTest {

    @Test
    void parseReturnsEmptyWhenRangeHeaderIsMissing() {
        assertThat(StorageDownloadRange.parse(null, 100L)).isEmpty();
        assertThat(StorageDownloadRange.parse(" ", 100L)).isEmpty();
    }

    @Test
    void parseSupportsExplicitSingleRange() {
        Optional<StorageDownloadRange> range = StorageDownloadRange.parse("bytes=10-19", 100L);

        assertThat(range).hasValue(new StorageDownloadRange(10L, 19L));
        assertThat(range.orElseThrow().length()).isEqualTo(10L);
        assertThat(range.orElseThrow().toContentRangeHeader(100L)).isEqualTo("bytes 10-19/100");
    }

    @Test
    void parseSupportsOpenEndedRange() {
        Optional<StorageDownloadRange> range = StorageDownloadRange.parse("bytes=95-", 100L);

        assertThat(range).hasValue(new StorageDownloadRange(95L, 99L));
    }

    @Test
    void parseSupportsSuffixRange() {
        Optional<StorageDownloadRange> range = StorageDownloadRange.parse("bytes=-10", 100L);

        assertThat(range).hasValue(new StorageDownloadRange(90L, 99L));
    }

    @Test
    void parseClampsRangeEndToTotalLength() {
        Optional<StorageDownloadRange> range = StorageDownloadRange.parse("bytes=90-120", 100L);

        assertThat(range).hasValue(new StorageDownloadRange(90L, 99L));
    }

    @Test
    void parseRejectsUnsupportedOrUnsatisfiableRanges() {
        assertThatThrownBy(() -> StorageDownloadRange.parse("items=0-10", 100L))
                .isInstanceOf(InvalidDownloadRangeException.class)
                .hasMessage("请求的下载区间无效。");
        assertThatThrownBy(() -> StorageDownloadRange.parse("bytes=10-5", 100L))
                .isInstanceOf(InvalidDownloadRangeException.class);
        assertThatThrownBy(() -> StorageDownloadRange.parse("bytes=0-1,3-4", 100L))
                .isInstanceOf(InvalidDownloadRangeException.class);
        assertThatThrownBy(() -> StorageDownloadRange.parse("bytes=100-120", 100L))
                .isInstanceOf(InvalidDownloadRangeException.class)
                .extracting("totalLength")
                .isEqualTo(100L);
    }
}
