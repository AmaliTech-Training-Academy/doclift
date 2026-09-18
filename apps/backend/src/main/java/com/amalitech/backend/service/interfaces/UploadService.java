package com.amalitech.backend.service.interfaces;

import com.amalitech.backend.model.Job;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {

    Job handleUpload(MultipartFile file);
}
