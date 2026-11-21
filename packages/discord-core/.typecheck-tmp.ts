import { Client } from 'discord.js';
import type { DiscordClient } from './src/index.js';

declare const client: Client;
const compatible: DiscordClient = client;
void compatible;
