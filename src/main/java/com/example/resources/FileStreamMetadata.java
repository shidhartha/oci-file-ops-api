package com.example.resources;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.InputStream;

@Getter
@Setter
@AllArgsConstructor
public class FileStreamMetadata {
    private final InputStream inputStream;
    private final String md5Hash;
    private final long size;
}
