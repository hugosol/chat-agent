package com.hugosol.chatagent.dto;

import java.time.Instant;

public record UserListItem(String id, String username, Instant createTime, boolean enabled) {}
