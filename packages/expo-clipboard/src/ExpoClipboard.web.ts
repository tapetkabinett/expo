import { GetStringOptions, SetStringOptions } from './Clipboard.types';

export default {
  get name(): string {
    return 'ExpoClipboard';
  },
  async getStringAsync(_options: GetStringOptions): Promise<string> {
    let text = '';
    try {
      text = await navigator.clipboard.readText();
    } catch (e) {
      try {
        // Internet Explorer
        // @ts-ignore
        text = window.clipboardData.getData('Text');
      } catch (e) {
        return Promise.reject(new Error('Unable to retrieve item from clipboard.'));
      }
    }
    return text;
  },
  setString(text: string): boolean {
    let success = false;
    const textField = document.createElement('textarea');
    textField.textContent = text;
    document.body.appendChild(textField);
    textField.select();
    try {
      document.execCommand('copy');
      success = true;
    } catch (e) {}
    document.body.removeChild(textField);
    return success;
  },
  async setStringAsync(text: string, _options: SetStringOptions): Promise<boolean> {
    return this.setString(text);
  },
  async hasStringAsync(): Promise<boolean> {
    return this.getStringAsync({}).then((text) => text.length > 0);
  },
  addClipboardListener(): void {},
  removeClipboardListener(): void {},
};
