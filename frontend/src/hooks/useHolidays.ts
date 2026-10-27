import { useEffect, useMemo, useState } from 'react'
import { listHolidays } from '../api/leave'
import type { Holiday } from '../types/leave'

/** Loads public holidays for the given years (each year fetched once) and indexes them by date. */
export function useHolidays(years: number[]): Map<string, Holiday> {
  const [byYear, setByYear] = useState<Record<number, Holiday[]>>({})
  const key = [...new Set(years)].sort().join(',')

  useEffect(() => {
    const wanted = key.split(',').filter(Boolean).map(Number)
    wanted
      .filter((y) => byYear[y] === undefined)
      .forEach((y) => {
        listHolidays(y)
          .then((list) => setByYear((prev) => ({ ...prev, [y]: list })))
          .catch(() => setByYear((prev) => ({ ...prev, [y]: [] })))
      })
    // byYear is read only to skip years that are already loaded
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  return useMemo(() => {
    const map = new Map<string, Holiday>()
    Object.values(byYear).flat().forEach((h) => map.set(h.holidayDate, h))
    return map
  }, [byYear])
}
