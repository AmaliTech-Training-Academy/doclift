export interface UploadApiResponse {
  jobId: number;
}

export interface ApiErrorResponse {
  code?: string;
  message?: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "";

export async function uploadFile(file: File): Promise<UploadApiResponse> {
  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/uploads`, {
      method: "POST",
      body: formData,
    });

    if (!response.ok) {
      // 413: Spring may not return a JSON body for payload too large
      if (response.status === 413) {
        throw new Error("File is too large. Please upload a smaller PDF.");
      }

      let errorMessage = "Failed to upload document.";
      try {
        const errorData: ApiErrorResponse = await response.json();
        if (errorData.message) {
          errorMessage = errorData.message;
        }
      } catch {
        errorMessage = `Upload failed with status code ${response.status}.`;
      }
      throw new Error(errorMessage);
    }

    const data: UploadApiResponse = await response.json();
    return data;
  } catch (err: unknown) {
    if (err instanceof Error) {
      throw err;
    }
    throw new Error("An unexpected error occurred while connecting to the conversion service.");
  }
}
