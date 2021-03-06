/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.metrics;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.opentelemetry.api.internal.Utils.checkArgument;
import static java.util.Objects.requireNonNull;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Builder utility for this exporter. */
public final class OtlpGrpcMetricExporterBuilder {

  private static final Logger logger =
      Logger.getLogger(OtlpGrpcMetricExporterBuilder.class.getName());

  private static final String DEFAULT_ENDPOINT_URL = "http://localhost:4317";
  private static final URI DEFAULT_ENDPOINT = URI.create(DEFAULT_ENDPOINT_URL);
  private static final long DEFAULT_TIMEOUT_SECS = 10;

  private ManagedChannel channel;
  private long timeoutNanos = TimeUnit.SECONDS.toNanos(DEFAULT_TIMEOUT_SECS);
  private URI endpoint = DEFAULT_ENDPOINT;
  private boolean useTls = false;

  @Nullable private Metadata metadata;

  /**
   * Sets the managed chanel to use when communicating with the backend. Takes precedence over
   * {@link #setEndpoint(String)} if both are called.
   *
   * @param channel the channel to use
   * @return this builder's instance
   */
  public OtlpGrpcMetricExporterBuilder setChannel(ManagedChannel channel) {
    this.channel = channel;
    return this;
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of metrics. If
   * unset, defaults to {@value DEFAULT_TIMEOUT_SECS}s.
   */
  public OtlpGrpcMetricExporterBuilder setTimeout(long timeout, TimeUnit unit) {
    requireNonNull(unit, "unit");
    checkArgument(timeout >= 0, "timeout must be non-negative");
    timeoutNanos = unit.toNanos(timeout);
    return this;
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of metrics. If
   * unset, defaults to {@value DEFAULT_TIMEOUT_SECS}s.
   */
  public OtlpGrpcMetricExporterBuilder setTimeout(Duration timeout) {
    requireNonNull(timeout, "timeout");
    return setTimeout(timeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  /**
   * Sets the OTLP endpoint to connect to. If unset, defaults to {@value DEFAULT_ENDPOINT_URL}. The
   * endpoint must start with either http:// or https://.
   */
  public OtlpGrpcMetricExporterBuilder setEndpoint(String endpoint) {
    requireNonNull(endpoint, "endpoint");
    final URI uri;
    try {
      uri = new URI(endpoint);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid endpoint, must be a URL: " + endpoint, e);
    }
    // TODO(anuraaga): Remove after announcing deprecation of schemaless URLs.
    if (uri.getScheme() != null) {
      if (!uri.getScheme().equals("http") && !uri.getScheme().equals("https")) {
        throw new IllegalArgumentException("Invalid scheme, must be http or https: " + endpoint);
      }
    } else {
      logger.log(
          Level.WARNING,
          "Endpoints must have a scheme of http:// or https://. Endpoints without schemes will "
              + "not be permitted in a future version of the SDK.",
          new Throwable());
    }
    this.endpoint = uri;
    return this;
  }

  /**
   * Sets use or not TLS, default is false. Optional. Applicable only if {@link
   * OtlpGrpcMetricExporterBuilder#endpoint} is set to build channel.
   *
   * @param useTls use TLS or not
   * @return this builder's instance
   * @deprecated Pass a URL starting with https:// to {@link #setEndpoint(String)}
   */
  @Deprecated
  public OtlpGrpcMetricExporterBuilder setUseTls(boolean useTls) {
    this.useTls = useTls;
    return this;
  }

  /**
   * Add header to request. Optional. Applicable only if {@link
   * OtlpGrpcMetricExporterBuilder#endpoint} is set to build channel.
   *
   * @param key header key
   * @param value header value
   * @return this builder's instance
   */
  public OtlpGrpcMetricExporterBuilder addHeader(String key, String value) {
    if (metadata == null) {
      metadata = new Metadata();
    }
    metadata.put(Metadata.Key.of(key, ASCII_STRING_MARSHALLER), value);
    return this;
  }

  /**
   * Constructs a new instance of the exporter based on the builder's values.
   *
   * @return a new exporter's instance
   */
  public OtlpGrpcMetricExporter build() {
    if (channel == null) {
      final ManagedChannelBuilder<?> managedChannelBuilder =
          ManagedChannelBuilder.forTarget(endpoint.getAuthority());

      if (endpoint.getScheme().equals("https") || useTls) {
        managedChannelBuilder.useTransportSecurity();
      } else {
        managedChannelBuilder.usePlaintext();
      }

      if (metadata != null) {
        managedChannelBuilder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata));
      }

      channel = managedChannelBuilder.build();
    }
    return new OtlpGrpcMetricExporter(channel, timeoutNanos);
  }

  OtlpGrpcMetricExporterBuilder() {}
}
