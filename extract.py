import json  
for line in open(r'C:\Users\SokolovAnV\.gemini\antigravity-ide\brain\26816d14-5620-4fb5-b8c8-3ee453250fe4\.system_generated\logs\transcript_full.jsonl', encoding='utf-8'):  
    if '6180' in line and 'CodeContent' in line:  
        data = json.loads(line)  
        if 'tool_calls' in data:  
            for tc in data['tool_calls']:  
                if 'args' in tc and 'CodeContent' in tc['args']:  
                    with open('plan_temp.txt', 'w', encoding='utf-8') as f:  
                        f.write(tc['args']['CodeContent'].replace('\\n', '\n')) 
