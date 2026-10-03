package com.sonkkeut.backend.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreRequest(@NotBlank @Size(max = 100) String name) {
}
