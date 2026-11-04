export const PHOTO_UPLOAD_POLICY = {
  maxDimension: 1920,
  maxBytes: 5 * 1024 * 1024,
  initialQuality: 0.82,
  minimumQuality: 0.5,
  qualityStep: 0.08,
} as const

type DecodedPhoto = ImageBitmap | HTMLImageElement

async function decodePhoto(file: File): Promise<DecodedPhoto> {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file, { imageOrientation: 'from-image' })
  }

  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('PHOTO_DECODE_FAILED'))
    }
    image.src = url
  })
}

function canvasBlob(canvas: HTMLCanvasElement, quality: number): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(blob => {
      if (blob) resolve(blob)
      else reject(new Error('PHOTO_COMPRESSION_FAILED'))
    }, 'image/jpeg', quality)
  })
}

export async function compressPhotoForUpload(file: File): Promise<File> {
  if (!['image/jpeg', 'image/png'].includes(file.type)) {
    throw new Error('PHOTO_FORMAT_UNSUPPORTED')
  }

  const source = await decodePhoto(file)
  const scale = Math.min(1, PHOTO_UPLOAD_POLICY.maxDimension / Math.max(source.width, source.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(source.width * scale))
  canvas.height = Math.max(1, Math.round(source.height * scale))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('PHOTO_COMPRESSION_UNAVAILABLE')
  context.drawImage(source, 0, 0, canvas.width, canvas.height)
  if ('close' in source) source.close()

  for (let quality = PHOTO_UPLOAD_POLICY.initialQuality;
       quality >= PHOTO_UPLOAD_POLICY.minimumQuality;
       quality -= PHOTO_UPLOAD_POLICY.qualityStep) {
    const blob = await canvasBlob(canvas, quality)
    if (blob.size <= PHOTO_UPLOAD_POLICY.maxBytes) {
      const name = file.name.replace(/\.[^.]+$/, '') || 'dodu-photo'
      return new File([blob], `${name}.jpg`, { type: 'image/jpeg', lastModified: file.lastModified })
    }
  }

  throw new Error('PHOTO_TOO_LARGE_AFTER_COMPRESSION')
}
