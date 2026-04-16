package org.openclaw.agentcommproxy.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 消息列表响应 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListResponse {

    @JsonProperty("total")
    private int total;

    @JsonProperty("records")
    private List<StatusResponse> records;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<StatusResponse> getRecords() {
        return records;
    }

    public void setRecords(List<StatusResponse> records) {
        this.records = records;
    }
}