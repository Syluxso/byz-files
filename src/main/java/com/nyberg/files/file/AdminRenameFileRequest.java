package com.nyberg.files.file;

import jakarta.validation.constraints.NotBlank;

public record AdminRenameFileRequest(@NotBlank String name) {}
