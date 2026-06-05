package com.hugosol.chatagent.dto;

import java.time.LocalDateTime;

public record UserListItem(String id, String username, LocalDateTime createTime, boolean enabled) {}
