
import { apiRequest } from '@/api/client'
import type { Course } from '@/api/course'

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'READY'
  | 'FAILED'
  | 'DELETING'
  | 'DELETE_FAILED'
  | 'DELETED'

export interface KnowledgeDoc {
  id: number
  courseId: number
  uploaderId: number
  originalName: string
  fileSize: number
  status: DocumentStatus
  errorMessage: string | null
  createTime: string
}

export function listDocs(
  courseId: number,
): Promise<KnowledgeDoc[]> {
  return apiRequest<KnowledgeDoc[]>(
    `/documents/course/${encodeURIComponent(courseId)}`,
  )
}

export function uploadDoc(
  courseId: number,
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

export interface DeleteDocResult {
  message?: string
}

export function deleteDoc(
  docId: number,
): Promise<DeleteDocResult> {
  return apiRequest<DeleteDocResult>(
    `/documents/${encodeURIComponent(docId)}`,
    {
      method: 'DELETE',
    },
  )
}

export function retryDoc(
  docId: number,
): Promise<string> {
  return apiRequest<string>(
    `/documents/${encodeURIComponent(docId)}/retry`,
    {
      method: 'POST',
    },
  )
}

export function downloadDoc(
  docId: number,
): string {
  return `/api/documents/${encodeURIComponent(docId)}/download`
}
