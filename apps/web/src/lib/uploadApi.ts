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
      // some proxy layers may strip it — fall back to a hardcoded message.
      if (response.status === 413) {
        let backendMessage: string | null = null;
        try {
          const errorData: ApiErrorResponse = await response.json();
          if (errorData.message) backendMessage = errorData.message;
        } catch {
          // JSON body absent or malformed — use fallback below
        }
        throw new Error(
          backendMessage ?? "File is too large. Please upload a PDF smaller than 10MB."
        );
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
