import { Message } from '@/models/Message';
import { TEMP_PARTICIPANTS } from './tempParticipants';

export const TEMP_MESSAGES: Message[] = [
    { x500name: TEMP_PARTICIPANTS[0], message: 'Hey' },
    { x500name: TEMP_PARTICIPANTS[1], message: 'Hey there!' },
    {
        x500name: TEMP_PARTICIPANTS[2],
        message:
            'Hey there!, this is a longer message to test stufff.. not Lorem Ipsum not not not. MHHHHH how are you?',
    },
    { x500name: TEMP_PARTICIPANTS[3], message: 'Hey there!' },
    { x500name: TEMP_PARTICIPANTS[4], message: 'Hey there!' },
    { x500name: TEMP_PARTICIPANTS[5], message: 'Hey there!' },
    { x500name: TEMP_PARTICIPANTS[6], message: 'Hey there!' },
    { x500name: TEMP_PARTICIPANTS[7], message: 'Hey there!' },
    { x500name: TEMP_PARTICIPANTS[8], message: 'Hey there!' },
];
