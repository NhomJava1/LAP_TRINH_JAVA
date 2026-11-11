export class SdkError extends Error {
  constructor(public status: number, public title: string, public detail?: string) {
    super(`HTTP Error ${status}: ${title} - ${detail || ''}`);
    this.name = 'SdkError';
  }
}