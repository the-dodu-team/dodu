export type PromiseTool = 'computer' | 'book'

export type PromiseDraft = {
  taskName: string
  startTime: string
  tool: PromiseTool
}

export type PreviewPromise = PromiseDraft & {
  date: string
  status: 'pending' | 'verified'
}
