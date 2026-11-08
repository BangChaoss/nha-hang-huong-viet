import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { reportApi, type ComprehensiveReport, type ReportParams } from "../api/report-api";

export function useReportOverview(params: ReportParams, enabled = true) {
  return useQuery<ComprehensiveReport, Error>({
    queryKey: ["report-overview", params.filterType, params.from, params.to, params.dishLimit],
    queryFn: () => reportApi.getOverview(params),
    enabled,
    // Giữ dữ liệu cũ khi đổi bộ lọc để giao diện không nhấp nháy.
    placeholderData: keepPreviousData,
    staleTime: 30_000,
  });
}
