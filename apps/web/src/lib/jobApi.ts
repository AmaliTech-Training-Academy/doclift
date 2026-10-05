export type BackendJobStatus = "QUEUED" | "PROCESSING" | "DONE" | "FAILED";

export interface JobStatusResponse {
  jobId: string;
  status: BackendJobStatus;
  sourceFilename: string;
  pageCount: number | null;
  createdAt: string;
}

export interface ApiErrorResponse {
  error?: string;
  message?: string;
}

async function extractErrorMessage(response: Response): Promise<string | null> {
  try {
    const errorData: ApiErrorResponse = await response.json();
    return errorData.message || null;
  } catch {
    return null;
  }
}

/**
 * Fetches the current conversion status for a given job ID.
 */
export async function getJobStatus(
  jobId: string,
  signal?: AbortSignal
): Promise<JobStatusResponse> {
  try {
    const response = await fetch(`/api/v1/jobs/${encodeURIComponent(jobId)}`, {
      method: "GET",
      signal,
    });

    if (!response.ok) {
      const backendMessage = await extractErrorMessage(response);

      if (response.status === 404) {
        throw new Error(backendMessage ?? "Job not found.");
      }

      throw new Error(
        backendMessage ?? `Failed to fetch job status (status code ${response.status}).`
      );
    }

    const data: JobStatusResponse = await response.json();
    return data;
  } catch (err: unknown) {
    if (err instanceof Error) {
      throw err;
    }
    throw new Error("An unexpected error occurred while fetching job status.");
  }
}