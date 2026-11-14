package com.edu.ai.task.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

@Data
public class DraftDecisionRequest {

    @NotNull(message = "决策类型不能为空")
    private Decision decision;

    @NotBlank(message = "请求编号不能为空")
    @JsonProperty("client_request_id")
    private String clientRequestId;

    public enum Decision {

        CONFIRM_UPDATE,

        CREATE_NEW,

        DISCARD
    }
}
