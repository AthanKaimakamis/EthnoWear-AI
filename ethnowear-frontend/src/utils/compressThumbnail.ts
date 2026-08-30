const MAX_DIMENSION = 1280
const INITIAL_QUALITY = 0.82
const MIN_QUALITY = 0.55
const TARGET_SIZE = 512 * 1024

export async function compressThumbnail(file: File): Promise<File> {
    if (!file.type.startsWith('image/')) return file

    try {
        const image = await loadImage(file)
        const scale = Math.min(1, MAX_DIMENSION / Math.max(image.naturalWidth, image.naturalHeight))
        const canvas = document.createElement('canvas')
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
        const context = canvas.getContext('2d')
        if (!context) return file

        context.fillStyle = '#fff'
        context.fillRect(0, 0, canvas.width, canvas.height)
        context.drawImage(image, 0, 0, canvas.width, canvas.height)

        let quality = INITIAL_QUALITY
        let blob = await canvasBlob(canvas, quality)
        while (blob.size > TARGET_SIZE && quality > MIN_QUALITY) {
            quality = Math.max(MIN_QUALITY, quality - 0.09)
            blob = await canvasBlob(canvas, quality)
        }

        if (blob.size >= file.size) return file
        return new File([blob], jpegName(file.name), { type: 'image/jpeg', lastModified: Date.now() })
    } catch {
        return file
    }
}

function loadImage(file: File) {
    return new Promise<HTMLImageElement>((resolve, reject) => {
        const url = URL.createObjectURL(file)
        const image = new Image()
        image.onload = () => { URL.revokeObjectURL(url); resolve(image) }
        image.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Thumbnail could not be decoded')) }
        image.src = url
    })
}

function canvasBlob(canvas: HTMLCanvasElement, quality: number) {
    return new Promise<Blob>((resolve, reject) => {
        canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Thumbnail could not be compressed')), 'image/jpeg', quality)
    })
}

function jpegName(name: string) {
    const dot = name.lastIndexOf('.')
    return `${dot > 0 ? name.slice(0, dot) : name}.jpg`
}
