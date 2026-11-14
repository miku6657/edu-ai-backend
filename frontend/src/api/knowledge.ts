
import { apiRequest } from '@/api/client'
import type { BigIntId } from '@/api/types'

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'READY'
  | 'FAILED'
  | 'DELETING'
  | 'DELETE_FAILED'
  | 'DELETED'

export interface KnowledgeDoc {
  id: BigIntId
  courseId: BigIntId
  uploaderId: BigIntId
  originalName: string
  fileSize: number
  status: DocumentStatus
  errorMessage: string | null
  createTime: string
}

export function listDocs(
  courseId: BigIntId,
): Promise<KnowledgeDoc[]> {
  return apiRequest<KnowledgeDoc[]>(
    `/documents/course/${encodeURIComponent(courseId)}`,
  )
}

export function uploadDoc(
  courseId: BigIntId,
  file: File,
): Promise<KnowledgeDoc> {
  const form = new FormData()
  form.append('file', file)

  return apiRequest<KnowledgeDoc>(
    `/documents/course/${encodeURIComponent(courseId)}`,
    {
      method: 'POST',
      formData: form,
    },
  )
}

export function deleteDoc(
  docId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/documents/${encodeURIComponent(docId)}`,
    {
      method: 'DELETE',
    },
  )
}

export function retryDoc(
  docId: BigIntId,
): Promise<string> {
  return apiRequest<string>(
    `/documents/${encodeURIComponent(docId)}/retry`,
    {
      method: 'POST',
    },
  )
}

export function downloadDoc(
  docId: BigIntId,
): string {
  return `/api/documents/${encodeURIComponent(docId)}/download`
}
