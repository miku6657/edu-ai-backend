package com.edu.ai.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PreferencesUpdateRequest {

    @NotBlank(message = "用户时区不能为空")
    private String timezone;

    @JsonProperty("dnd_start_time")
    @NotBlank(message = "免打扰开始时间不能为空")
    private String dndStartTime;

    @JsonProperty("dnd_end_time")
    @NotBlank(message = "免打扰结束时间不能为空")
    private String dndEndTime;

    @JsonProperty("bound_im_platform")
    private String boundImPlatform;

    @JsonProperty("bound_im_uid")
    private String boundImUid;
}
