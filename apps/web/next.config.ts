import type { NextConfig } from "next";

// Defaults to localhost for local development; override in staging/production.
const BACKEND_ORIGIN = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${BACKEND_ORIGIN}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
