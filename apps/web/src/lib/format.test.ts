import { describe,it,expect,vi,afterEach } from "vitest";
import { money,time,travelDate } from "./format";
describe("journey presentation",()=>{
  afterEach(()=>vi.useRealTimers());
  it("formats INR without changing server amounts",()=>{expect(money({amount:"12345.50",currency:"INR"})).toBe("₹12,345.50");});
  it("renders an instant in journey-local time",()=>{expect(time("2026-11-01T02:30:00Z")).toBe("08:00");});
  it("defaults dates using India calendar rollover",()=>{
    vi.useFakeTimers();vi.setSystemTime(new Date("2026-10-31T23:00:00Z"));
    expect(travelDate()).toBe("2026-11-01");expect(travelDate(1)).toBe("2026-11-02");
  });
});
