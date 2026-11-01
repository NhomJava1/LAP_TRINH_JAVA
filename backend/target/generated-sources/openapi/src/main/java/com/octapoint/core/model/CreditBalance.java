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
 * CreditBalance
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-10-23T19:21:19.276347+07:00[Asia/Ho_Chi_Minh]")
public class CreditBalance {

  private String customerId;

  private Integer balance;

  private String settlementStatus;

  public CreditBalance customerId(String customerId) {
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

  public CreditBalance balance(Integer balance) {
    this.balance = balance;
    return this;
  }

  /**
   * Get balance
   * @return balance
  */
  
  @Schema(name = "balance", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("balance")
  public Integer getBalance() {
    return balance;
  }

  public void setBalance(Integer balance) {
    this.balance = balance;
  }

  public CreditBalance settlementStatus(String settlementStatus) {
    this.settlementStatus = settlementStatus;
    return this;
  }

  /**
   * Get settlementStatus
   * @return settlementStatus
  */
  
  @Schema(name = "settlementStatus", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("settlementStatus")
  public String getSettlementStatus() {
    return settlementStatus;
  }

  public void setSettlementStatus(String settlementStatus) {
    this.settlementStatus = settlementStatus;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreditBalance creditBalance = (CreditBalance) o;
    return Objects.equals(this.customerId, creditBalance.customerId) &&
        Objects.equals(this.balance, creditBalance.balance) &&
        Objects.equals(this.settlementStatus, creditBalance.settlementStatus);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId, balance, settlementStatus);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreditBalance {\n");
    sb.append("    customerId: ").append(toIndentedString(customerId)).append("\n");
    sb.append("    balance: ").append(toIndentedString(balance)).append("\n");
    sb.append("    settlementStatus: ").append(toIndentedString(settlementStatus)).append("\n");
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

