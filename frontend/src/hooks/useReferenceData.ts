import { useEffect, useState } from 'react'
import { listDirectory } from '../api/employees'
import { listDepartments, listDesignations } from '../api/organization'
import type { ReferenceItem } from '../types/employee'

export interface PersonOption {
  id: number
  name: string
}

/** Live dropdown data: active departments and designations, plus people (for manager pickers). */
export function useReferenceData() {
  const [departments, setDepartments] = useState<ReferenceItem[]>([])
  const [designations, setDesignations] = useState<ReferenceItem[]>([])
  const [people, setPeople] = useState<PersonOption[]>([])

  useEffect(() => {
    let cancelled = false
    Promise.all([listDepartments(), listDesignations(), listDirectory('', 0, 200)])
      .then(([d, g, p]) => {
        if (cancelled) return
        setDepartments(d.map((x) => ({ id: x.id, name: x.name, departmentId: null })))
        setDesignations(g.map((x) => ({ id: x.id, name: x.title, departmentId: x.departmentId })))
        setPeople(p.content.map((e) => ({ id: e.id, name: `${e.fullName} (${e.jobTitle})` })))
      })
      .catch(() => {
        /* dropdowns stay empty; the page shows its own errors for real actions */
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { departments, designations, people }
}
