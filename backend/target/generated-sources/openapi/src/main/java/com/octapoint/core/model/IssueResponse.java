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
 * IssueResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-23T19:21:19.276347+07:00[Asia/Ho_Chi_Minh]")
public class IssueResponse {

  private String transactionId;

  private Integer pointsIssued;

  private String status;

  public IssueResponse transactionId(String transactionId) {
    this.transactionId = transactionId;
    return this;
  }

  /**
   * Get transactionId
   * @return transactionId
  */
  
  @Schema(name = "transactionId", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("transactionId")
  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public IssueResponse pointsIssued(Integer pointsIssued) {
    this.pointsIssued = pointsIssued;
    return this;
  }

  /**
   * Get pointsIssued
   * @return pointsIssued
  */
  
  @Schema(name = "pointsIssued", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("pointsIssued")
  public Integer getPointsIssued() {
    return pointsIssued;
  }

  public void setPointsIssued(Integer pointsIssued) {
    this.pointsIssued = pointsIssued;
  }

  public IssueResponse status(String status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
  */
  
  @Schema(name = "status", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    IssueResponse issueResponse = (IssueResponse) o;
    return Objects.equals(this.transactionId, issueResponse.transactionId) &&
        Objects.equals(this.pointsIssued, issueResponse.pointsIssued) &&
        Objects.equals(this.status, issueResponse.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactionId, pointsIssued, status);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class IssueResponse {\n");
    sb.append("    transactionId: ").append(toIndentedString(transactionId)).append("\n");
    sb.append("    pointsIssued: ").append(toIndentedString(pointsIssued)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
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

