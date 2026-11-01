package com.octapoint.core.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * IssueRequest
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-23T19:21:19.276347+07:00[Asia/Ho_Chi_Minh]")
public class IssueRequest {

  private String customerId;

  private Integer amountVnd;

  public IssueRequest customerId(String customerId) {
    this.customerId = customerId;
    return this;
  }

  /**
   * Get customerId
   * @return customerId
  */
  
  @Schema(name = "customerId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("customerId")
  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(String customerId) {
    this.customerId = customerId;
  }

  public IssueRequest amountVnd(Integer amountVnd) {
    this.amountVnd = amountVnd;
    return this;
  }

  /**
   * Get amountVnd
   * @return amountVnd
  */
  
  @Schema(name = "amountVnd", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("amountVnd")
  public Integer getAmountVnd() {
    return amountVnd;
  }

  public void setAmountVnd(Integer amountVnd) {
    this.amountVnd = amountVnd;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IssueRequest issueRequest = (IssueRequest) o;
    return Objects.equals(this.customerId, issueRequest.customerId) &&
        Objects.equals(this.amountVnd, issueRequest.amountVnd);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, amountVnd);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IssueRequest {\n");
    sb.append("    customerId: ").append(toIndentedString(customerId)).append("\n");
    sb.append("    amountVnd: ").append(toIndentedString(amountVnd)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

