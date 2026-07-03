declare module "leo-profanity" {
  interface LeoProfanity {
    check(text: string): boolean;
    clean(text: string, replaceKey?: string): string;
    add(words: string | string[]): void;
    remove(words: string | string[]): void;
    loadDictionary(name?: string): void;
  }
  const filter: LeoProfanity;
  export default filter;
}
